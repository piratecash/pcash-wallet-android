package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class MoneroKitWrapperTestFixture {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    private val controlledRefreshCoordinatorJobs = mutableListOf<Job>()

    @After
    fun cancelControlledRefreshCoordinatorJobs() {
        controlledRefreshCoordinatorJobs.forEach(Job::cancel)
        controlledRefreshCoordinatorJobs.clear()
    }

    protected val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    protected val account = Account(
        id = "account-id",
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = emptyList(),
            password = "password",
            height = 1,
            walletInnerName = "wallet"
        ),
        origin = AccountOrigin.Created,
        level = 0
    )
    protected val unsupportedAccount = account.copy(
        type = AccountType.EvmAddress("0x1234")
    )
    protected val trezorAccount = account.copy(
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
    )

    protected fun mockService(): MoneroWalletService {
        return mockk(relaxed = true)
    }

    protected fun connectivity(connected: Boolean): IConnectivityManager = mockk {
        every { isConnected } returns MutableStateFlow(connected)
    }

    protected fun pendingLiveRefreshFixture(scope: TestScope): PendingLiveRefreshFixture {
        val service = mockService()
        val wrapper = createWrapper(
            service = service,
            account = trezorAccount,
            dispatcherProvider = TestDispatcherProvider(
                StandardTestDispatcher(scope.testScheduler),
                scope,
            ),
            healthReader = fakeHealthReader(healthyWalletHealth(hasUnknownKeyImages = false)),
        )
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        return PendingLiveRefreshFixture(
            wrapper = wrapper,
            reconciler = reconciler,
            session = checkNotNull(reconciler.activeSession()),
        )
    }

    protected fun createWrapper(
        service: MoneroWalletService,
        account: Account = this.account,
        tracker: NetworkErrorTracker = mockk(relaxed = true),
        connectivityManager: IConnectivityManager = connectivity(connected = true),
        gateway: MoneroTrezorOperationGateway = mockk(relaxed = true),
        dispatcherProvider: TestDispatcherProvider = this.dispatcherProvider,
        healthReader: MoneroWalletHealthReader = fakeHealthReader(healthyWalletHealth()),
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = tracker,
            moneroTrezorGateway = gateway,
            connectivityManager = connectivityManager,
            walletHealthReader = healthReader,
        )
    }

    protected fun invokeResolve(
        wrapper: MoneroKitWrapper,
        nativeConnected: Boolean,
        isSynchronized: Boolean,
        currentHeight: Long,
        totalHeight: Long,
    ): AdapterState = MoneroKitWrapper::class.java.getDeclaredMethod(
        "resolveSyncState",
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
    ).apply { isAccessible = true }
        .invoke(wrapper, nativeConnected, isSynchronized, currentHeight, totalHeight) as AdapterState

    protected fun invokePublish(wrapper: MoneroKitWrapper, state: AdapterState) {
        MoneroKitWrapper::class.java.getDeclaredMethod("publishSyncState", AdapterState::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, state)
    }

    protected fun fakeHealthReader(health: MoneroWalletHealthSnapshot): MoneroWalletHealthReader =
        object : MoneroWalletHealthReader {
            override fun snapshot(callbackWallet: Wallet?) = health
        }

    protected fun healthyWalletHealth(
        callbackWalletIsCurrent: Boolean = true,
        nativeStatusIsOk: Boolean = true,
        nativeConnectionIsConnected: Boolean = true,
        serviceConnectionIsConnected: Boolean = true,
        walletIsSynchronized: Boolean = true,
        nativeConnectionStatus: ConnectionStatus? = ConnectionStatus.ConnectionStatus_Connected,
        nativeStatusError: String? = null,
        hasUnknownKeyImages: Boolean? = null,
    ) = MoneroWalletHealthSnapshot(
        callbackWalletIsCurrent = callbackWalletIsCurrent,
        nativeStatusIsOk = nativeStatusIsOk,
        nativeConnectionIsConnected = nativeConnectionIsConnected,
        serviceConnectionIsConnected = serviceConnectionIsConnected,
        walletIsSynchronized = walletIsSynchronized,
        nativeConnectionStatus = nativeConnectionStatus,
        nativeStatusError = nativeStatusError,
        hasUnknownKeyImages = hasUnknownKeyImages,
    )

    protected fun invokeRecord(
        wrapper: MoneroKitWrapper,
        status: ConnectionStatus?,
        errorString: String?
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "recordNativeConnectionError", ConnectionStatus::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(wrapper, status, errorString)
    }

    protected fun invokeAbandonFaultedWallet(
        wrapper: MoneroKitWrapper,
        failure: HardwareWalletOperationException,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abandonFaultedWallet",
            HardwareWalletOperationException::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, failure)
    }

    protected fun invokeHandleStartFailure(wrapper: MoneroKitWrapper, failure: Exception) {
        MoneroKitWrapper::class.java.getDeclaredMethod("handleStartFailure", Exception::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, failure)
    }

    protected fun invokeAbortControlledHardwareWallet(wrapper: MoneroKitWrapper, error: Throwable) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abortControlledHardwareWallet",
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, error)
    }

    protected fun invokeActivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("activateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    protected fun invokeDeactivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("deactivateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    protected fun invokeSetSpendReadinessForSession(
        wrapper: MoneroKitWrapper,
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "setSpendReadinessForSession",
            Long::class.javaPrimitiveType,
            MoneroSpendReadiness::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, readiness)
    }

    protected fun invokeFailClosedAfterReconciliationError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterReconciliationError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    protected fun invokeFailClosedAfterPostSyncError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterPostSyncError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    protected fun activeReconciliationSession(wrapper: MoneroKitWrapper): Long {
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        return checkNotNull(reconciler.activeSession())
    }

    protected fun setKeyImageSyncSession(wrapper: MoneroKitWrapper, session: Long) {
        privateField(wrapper, "keyImageSyncSession").set(wrapper, session)
    }

    protected fun keyImageSyncSession(wrapper: MoneroKitWrapper): Long? =
        privateField(wrapper, "keyImageSyncSession").get(wrapper) as Long?

    protected fun setStarted(wrapper: MoneroKitWrapper) {
        privateField(wrapper, "isStarted").set(wrapper, true)
    }

    protected fun setExplicitColdRecoveryPending(wrapper: MoneroKitWrapper, pending: Boolean) {
        privateField(wrapper, "explicitColdRecoveryPending").set(wrapper, pending)
    }

    protected fun explicitColdRecoveryPending(wrapper: MoneroKitWrapper): Boolean =
        privateField(wrapper, "explicitColdRecoveryPending").getBoolean(wrapper)

    @Suppress("UNCHECKED_CAST")
    protected fun setSyncState(wrapper: MoneroKitWrapper, state: AdapterState) {
        val syncState = privateField(wrapper, "_syncState")
            .get(wrapper) as MutableStateFlow<AdapterState>
        syncState.value = state
        privateField(wrapper, "nativeSyncState").set(wrapper, state)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun setSpendReadiness(
        wrapper: MoneroKitWrapper,
        readiness: MoneroSpendReadiness,
    ) {
        val spendReadiness = privateField(wrapper, "_spendReadiness")
            .get(wrapper) as MutableStateFlow<MoneroSpendReadiness>
        spendReadiness.value = readiness
    }

    protected fun testControlledRefreshOperation(
        refresh: suspend (ControlledHardwareRefreshAborter) -> Unit = {},
        finalize: suspend () -> Unit = {},
        abort: (Throwable) -> Unit = {},
    ): ControlledHardwareRefreshOperation = object : ControlledHardwareRefreshOperation {
        override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
            try {
                refresh(aborter)
                finalize()
            } catch (error: Throwable) {
                aborter.abort(error)
                throw error
            }
        }

        override fun abort(error: Throwable) {
            abort(error)
        }
    }

    protected fun TestScope.controlledRefreshCoordinator(): ControlledHardwareRefreshCoordinator {
        val supervisor = SupervisorJob()
        controlledRefreshCoordinatorJobs += supervisor
        return ControlledHardwareRefreshCoordinator(
            CoroutineScope(coroutineContext.minusKey(Job) + supervisor),
        )
    }

    protected fun coordinatorMutex(coordinator: ControlledHardwareRefreshCoordinator): Mutex =
        coordinator.javaClass.getDeclaredField("mutex").apply {
            isAccessible = true
        }.get(coordinator) as Mutex

    protected fun privateField(wrapper: MoneroKitWrapper, name: String) =
        wrapper.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }

    protected data class PendingLiveRefreshFixture(
        val wrapper: MoneroKitWrapper,
        val reconciler: MoneroSpentStatusReconciler,
        val session: Long,
    )

}

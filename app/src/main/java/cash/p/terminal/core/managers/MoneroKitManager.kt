package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.core.onPollingStartedSuspend
import cash.p.terminal.core.onPollingStoppedSuspend
import cash.p.terminal.core.MoneroRescanException
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.MoneroAdapter
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import com.m2049r.levin.util.NetCipherHelper
import com.m2049r.xmrwallet.data.DefaultNodes
import com.m2049r.xmrwallet.data.NodeInfo
import com.m2049r.xmrwallet.data.TxData
import com.m2049r.xmrwallet.data.UserNotes
import com.m2049r.xmrwallet.model.PendingTransaction
import com.m2049r.xmrwallet.model.TransactionInfo
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.offline.SignedRawMoneroTransaction
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.m2049r.xmrwallet.service.WalletCorruptedException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareKeyImageRefreshResult
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.m2049r.xmrwallet.util.Helper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.sizeInMb
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class MoneroKitManager(
    private val moneroWalletService: MoneroWalletService,
    private val backgroundManager: BackgroundManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val connectivityManager: ConnectivityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val moneroFileDao: MoneroFileDao,
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase,
    private val networkErrorTracker: NetworkErrorTracker,
) {
    // Serializes account-lifecycle mutations (activate / unlink / stop) so the process-global
    // NetCipherHelper observer factory is set/cleared without racing a concurrent teardown.
    private val accountMutex = Mutex()
    private val pollingSessionMutex = Mutex()
    private val deletingAccountIds = mutableSetOf<String>()
    private val pollingSessionCount = AtomicInteger(0)
    private val coroutineScope =
        CoroutineScope(dispatcherProvider.io + CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Coroutine error")
        })
    var moneroKitWrapper: MoneroKitWrapper? = null
    private var wrapperAccount: Account? = null
    private val lifecycleJobs = mutableListOf<Job>()

    private var useCount = AtomicInteger(0)
    var currentAccount: Account? = null
        private set
    private val moneroKitStoppedSubject = PublishSubject.create<Unit>()
    private val moneroTrezorGateway: MoneroTrezorOperationGateway by inject(
        MoneroTrezorOperationGateway::class.java,
    )

    val kitStoppedObservable: Observable<Unit>
        get() = moneroKitStoppedSubject

    internal suspend fun <T> withExclusiveNativeWallet(
        block: suspend () -> T,
    ): T = accountMutex.withLock {
        moneroKitWrapper?.withNativeWalletReleased(block) ?: block()
    }

    suspend fun getMoneroKitWrapper(account: Account): MoneroKitWrapper = accountMutex.withLock {
        checkAccountIsNotBeingDeleted(account)
        // stopKit also nulls moneroKitWrapper and clears the factory
        this.moneroKitWrapper?.takeUnless { currentAccount == account }?.let { stopKit() }

        if (this.moneroKitWrapper == null) {
            val accountType = account.type
            this.moneroKitWrapper = when (accountType) {
                is AccountType.MnemonicMonero,
                is AccountType.Mnemonic,
                is AccountType.TrezorDevice -> createKitInstance(account)

                else -> throw UnsupportedAccountException()
            }
            wrapperAccount = account
            // Install the passive network observer for THIS account before startKit triggers
            // node-selection pings, so transport errors are attributed to the active account.
            NetCipherHelper.setEventListenerFactory(
                NetworkErrorEventListener.Factory(BlockchainType.Monero, account.id, networkErrorTracker)
            )
            var retryOnLifecycleEvent = false
            try {
                startKit()
            } catch (e: Throwable) {
                val partial = moneroKitWrapper
                var cleanupSucceeded = partial == null
                withContext(NonCancellable) {
                    try {
                        partial?.stop()
                        cleanupSucceeded = true
                    } catch (stopError: Throwable) {
                        e.addSuppressed(stopError)
                    }
                }
                retryOnLifecycleEvent =
                    cleanupSucceeded &&
                        accountType is AccountType.TrezorDevice &&
                        partial?.isRestartableAfterFailedStart == true &&
                        e.isRetryableTrezorStartupFailure()
                if (cleanupSucceeded && !retryOnLifecycleEvent) {
                    clearKitState()
                }
                if (!retryOnLifecycleEvent) throw e
            }
            subscribeToEvents()
            useCount.set(0)
            currentAccount = account
        }

        useCount.incrementAndGet()
        requireNotNull(moneroKitWrapper)
    }

    private fun createKitInstance(
        account: Account,
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = moneroWalletService,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = networkErrorTracker,
            moneroTrezorGateway = moneroTrezorGateway,
            connectivityManager = connectivityManager,
        )
    }

    suspend fun unlink(account: Account) = accountMutex.withLock {
        if (account.id in deletingAccountIds) return@withLock
        if (account == currentAccount) {
            if (useCount.decrementAndGet() < 1) {
                try {
                    stopKit()
                } catch (error: Throwable) {
                    useCount.incrementAndGet()
                    throw error
                }
            }
        }
    }

    suspend fun deleteForAccount(
        account: Account,
        stopAdapters: suspend () -> Unit,
        deleteAccount: suspend () -> Unit,
    ) {
        withContext(NonCancellable) {
            accountMutex.withLock {
                deletingAccountIds += account.id
                if (account == currentAccount) cancelLifecycleJobs()
            }
        }
        var deletionCommitted = false
        try {
            pollingSessionMutex.withLock {
                accountMutex.withLock {
                    if (isKitOwnedBy(account)) {
                        stopKitForDeletion()
                    }
                    deleteAccount()
                    if (isKitOwnedBy(account)) {
                        clearKitState()
                        useCount.set(0)
                    }
                    deletionCommitted = true
                }
                try {
                    stopAdapters()
                } catch (error: Throwable) {
                    Timber.e(error, "Failed to stop Monero adapters after account deletion")
                }
            }
        } finally {
            withContext(NonCancellable) {
                accountMutex.withLock {
                    deletingAccountIds -= account.id
                    if (!deletionCommitted && account == currentAccount && lifecycleJobs.isEmpty()) {
                        subscribeToEvents()
                    }
                }
            }
        }
    }

    private suspend fun stopKitForDeletion() {
        try {
            closeKit(saveWallet = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Failed to stop Monero wallet before deletion")
            closeKit(saveWallet = false)
        }
    }

    private fun checkAccountIsNotBeingDeleted(account: Account) =
        check(account.id !in deletingAccountIds) { "Account is being deleted" }

    private fun isKitOwnedBy(account: Account) =
        account == wrapperAccount || account == currentAccount

    /**
     * Rescans [account] from [newHeight]. Operates only on the already-active wrapper
     * (never calls [getMoneroKitWrapper], which would bump [useCount] without a matching
     * [unlink] and leak it). For an inactive account, the wallet files are removed and the
     * new height persisted so the next [getMoneroKitWrapper] re-derives the wallet from it.
     */
    suspend fun rescan(account: Account, newHeight: Long) = accountMutex.withLock {
        if (currentAccount?.id == account.id) {
            moneroKitWrapper?.rescan(newHeight)
        } else if (account.type is AccountType.TrezorDevice) {
            restoreSettingsManager.savePendingMoneroRescan(account, newHeight)
        } else {
            // Same fail-loud ordering as MoneroKitWrapper.resetWalletAndRestart: require the old
            // files to be gone before deleting the DAO record and committing the new height, so a
            // failed removal can never leave a stale wallet file behind a claimed new height.
            val removed = removeMoneroWalletFilesUseCase(account)
            if (!removed) {
                throw MoneroRescanException("Failed to remove Monero wallet files for account ${account.id}")
            }
            moneroFileDao.deleteAssociatedRecord(account.id)
            restoreSettingsManager.saveMoneroRestoreHeight(account, newHeight)
        }
    }

    internal suspend fun <T> withPollingSession(
        block: suspend (MoneroKitWrapper) -> T,
    ): T? = pollingSessionMutex.withLock {
        val wrapper = accountMutex.withLock {
            moneroKitWrapper?.takeUnless { currentAccount?.id in deletingAccountIds }?.also {
                pollingSessionCount.onPollingStartedSuspend { resumeOrStartKit() }
            }
        } ?: return@withLock null
        try {
            block(wrapper)
        } finally {
            withContext(NonCancellable) {
                accountMutex.withLock {
                    pollingSessionCount.onPollingStoppedSuspend(backgroundManager) {
                        if (currentAccount?.id !in deletingAccountIds) stopAndSaveKit()
                    }
                }
            }
        }
    }

    private suspend fun stopKit(saveWallet: Boolean = true) {
        cancelLifecycleJobs()
        try {
            closeKit(saveWallet)
        } catch (error: Throwable) {
            if (currentAccount != null) subscribeToEvents()
            throw error
        }
        clearKitState()
    }

    private suspend fun closeKit(saveWallet: Boolean) =
        withContext(NonCancellable) { moneroKitWrapper?.stop(saveWallet) }

    private fun clearKitState() {
        currentAccount = null
        wrapperAccount = null
        moneroKitWrapper = null
        NetCipherHelper.setEventListenerFactory(null)
    }

    private suspend fun startKit() {
        moneroKitWrapper?.start()
    }

    private suspend fun stopAndSaveKit() {
        moneroKitWrapper?.stop(saveWallet = true)
    }

    private suspend fun resumeOrStartKit() {
        val wrapper = moneroKitWrapper ?: return
        if (!wrapper.resume()) {
            startKit()
        }
    }

    private suspend fun resumeOrStartKitOnLifecycleEvent(): Boolean = try {
        resumeOrStartKit()
        true
    } catch (error: Throwable) {
        if (error.isRetryableTrezorStartupFailure() && moneroKitWrapper?.isRestartableAfterFailedStart == true) {
            false
        } else {
            throw error
        }
    }

    private fun subscribeToEvents() {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()

        lifecycleJobs += coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                accountMutex.withLock {
                    if (state == BackgroundManagerState.EnterForeground) {
                        resumeOrStartKitOnLifecycleEvent()
                    } else if (state == BackgroundManagerState.EnterBackground) {
                        if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero)) {
                            stopAndSaveKit()
                        } else {
                            Timber.tag("TxPoller").d("MoneroKit staying alive")
                        }
                    }
                }
            }
        }
        lifecycleJobs += coroutineScope.launch {
            connectivityManager.networkAvailabilityFlow
                .onSubscription { emitConnectivityMissedAtStartup() }
                .collect { connected ->
                    accountMutex.withLock {
                        if (!connected) {
                            moneroKitWrapper?.onNetworkLost()
                        } else if (backgroundManager.inForeground) {
                            if (!resumeOrStartKitOnLifecycleEvent()) return@withLock
                            // resumeOrStartKit is a no-op for an already running kit, so without an
                            // explicit refresh the state would stay NotSynced until the next callback.
                            moneroKitWrapper?.refresh()
                        }
                    }
                }
        }
        lifecycleJobs += coroutineScope.launch {
            moneroKitWrapper?.let { w ->
                w.syncState.map { it is AdapterState.Synced }.distinctUntilChanged()
                    .collect { synced ->
                        if (synced) accountMutex.withLock { tryOrNull { w.saveSynced() } }
                    }
            }
        }
    }

    private suspend fun cancelLifecycleJobs() {
        lifecycleJobs.forEach { it.cancelAndJoin() }
        lifecycleJobs.clear()
    }

    // startKit() runs before the collector above subscribes and networkAvailabilityFlow has no
    // replay, so a change inside that window is dropped: re-emit it when the kit disagrees with
    // the device, and stay silent when they already agree.
    private suspend fun FlowCollector<Boolean>.emitConnectivityMissedAtStartup() {
        val connected = connectivityManager.isConnected.value
        val kitOffline = moneroKitWrapper?.syncState?.value is AdapterState.NotSynced
        when {
            !connected && !kitOffline -> emit(false)
            connected && kitOffline -> emit(true)
        }
    }
}

private fun Throwable.isRetryableTrezorStartupFailure(): Boolean =
    this is HardwareWalletOperationException && when (error) {
        HardwareWalletErrorCode.DeviceNotFound,
        HardwareWalletErrorCode.AcquireTimeout -> true
        else -> false
    }

private fun RestoreSettingsManager.saveMoneroRestoreHeight(
    account: Account,
    height: Long,
) {
    require(height >= 0) { "Monero restore height must be non-negative" }
    val settings = settings(account, BlockchainType.Monero)
    settings.birthdayHeight = height
    save(settings, account, BlockchainType.Monero)
}

internal interface MoneroNativeWalletRuntime {
    suspend fun <T> withExclusiveWallet(block: suspend () -> T): T
}

internal fun shouldApplyHardwareRestoreHeight(
    currentHeight: Long,
    targetHeight: Long,
    hasPendingRescan: Boolean,
): Boolean = hasPendingRescan || currentHeight != targetHeight

internal class DefaultMoneroNativeWalletRuntime(
    private val moneroKitManager: MoneroKitManager,
) : MoneroNativeWalletRuntime {
    override suspend fun <T> withExclusiveWallet(block: suspend () -> T): T =
        moneroKitManager.withExclusiveNativeWallet(block)
}

/**
 * A single read of the native/service facts that authorize a callback or hardware spend.
 * Keeping this boundary injectable lets JVM tests exercise callback handling without loading JNI.
 */
data class MoneroWalletHealthSnapshot(
    val callbackWalletIsCurrent: Boolean,
    val nativeStatusIsOk: Boolean,
    val nativeConnectionIsConnected: Boolean,
    val serviceConnectionIsConnected: Boolean,
    val walletIsSynchronized: Boolean,
    val nativeConnectionStatus: ConnectionStatus? = null,
    val nativeStatusError: String? = null,
    val hasUnknownKeyImages: Boolean? = null,
) {
    val isCurrentAndConnected: Boolean
        get() = callbackWalletIsCurrent &&
            nativeStatusIsOk &&
            nativeConnectionIsConnected &&
            serviceConnectionIsConnected

    val isFullyHealthy: Boolean
        get() = isCurrentAndConnected && walletIsSynchronized
}

interface MoneroWalletHealthReader {
    fun snapshot(callbackWallet: Wallet?): MoneroWalletHealthSnapshot
}

private class ServiceMoneroWalletHealthReader(
    private val moneroWalletService: MoneroWalletService,
) : MoneroWalletHealthReader {
    override fun snapshot(callbackWallet: Wallet?): MoneroWalletHealthSnapshot {
        val serviceConnectionIsConnected = tryOrNull { moneroWalletService.connectionStatus } ==
            ConnectionStatus.ConnectionStatus_Connected
        val callbackWalletIsCurrent = callbackWallet != null &&
            callbackWallet === tryOrNull { moneroWalletService.wallet }
        if (!callbackWalletIsCurrent) {
            return MoneroWalletHealthSnapshot(
                callbackWalletIsCurrent = false,
                nativeStatusIsOk = false,
                nativeConnectionIsConnected = false,
                serviceConnectionIsConnected = serviceConnectionIsConnected,
                walletIsSynchronized = false,
            )
        }
        val status = tryOrNull { callbackWallet.status }
        val nativeConnectionStatus = tryOrNull { callbackWallet.connectionStatus }
        return MoneroWalletHealthSnapshot(
            callbackWalletIsCurrent = true,
            nativeStatusIsOk = status?.isOk == true,
            nativeConnectionIsConnected =
                nativeConnectionStatus == ConnectionStatus.ConnectionStatus_Connected,
            serviceConnectionIsConnected = serviceConnectionIsConnected,
            walletIsSynchronized = tryOrNull { callbackWallet.isSynchronized } == true,
            nativeConnectionStatus = nativeConnectionStatus,
            nativeStatusError = status?.errorString,
        )
    }
}

private data class MoneroWalletCredentials(
    val fileName: String,
    val password: String,
)

private const val CONTROLLED_REFRESH_FINALIZATION_TIMEOUT_MILLIS = 30_000L

internal fun interface ControlledHardwareRefreshAborter {
    fun abort(error: Throwable)
}

internal interface ControlledHardwareRefreshOperation {
    suspend fun run(aborter: ControlledHardwareRefreshAborter)
    fun abort(error: Throwable)
}

internal class ControlledHardwareRefreshCoordinator(
    private val scope: CoroutineScope,
) {
    private class Generation(
        val operation: Deferred<Unit>,
        var waiterCount: Int,
    )

    private val mutex = Mutex()
    private var generation: Generation? = null

    suspend fun execute(refreshOperation: ControlledHardwareRefreshOperation) {
        val acquiredGeneration = acquireOperation(refreshOperation)
        try {
            acquiredGeneration.operation.await()
        } finally {
            withContext(NonCancellable) {
                releaseWaiter(acquiredGeneration)
            }
        }
    }

    private suspend fun acquireOperation(
        refreshOperation: ControlledHardwareRefreshOperation,
    ): Generation {
        while (true) {
            val acquiredGeneration = mutex.withLock {
                val current = generation
                when {
                    current == null || current.operation.isCompleted -> {
                        Generation(
                            operation = scope.async {
                                val aborter =
                                    OneShotControlledHardwareRefreshAborter(refreshOperation::abort)
                                // The operation owns its lifecycle boundary and must abort before
                                // releasing it. A coordinator-level fallback would run outside that
                                // boundary and could race a queued wallet stop/restart.
                                refreshOperation.run(aborter)
                            },
                            waiterCount = 1,
                        ).also { generation = it }
                    }

                    current.operation.isCancelled -> current
                    else -> {
                        current.waiterCount += 1
                        current
                    }
                }
            }
            if (!acquiredGeneration.operation.isCancelled) return acquiredGeneration
            acquiredGeneration.operation.join()
            mutex.withLock {
                if (
                    generation === acquiredGeneration &&
                    acquiredGeneration.waiterCount == 0 &&
                    acquiredGeneration.operation.isCompleted
                ) {
                    generation = null
                }
            }
        }
    }

    private suspend fun releaseWaiter(acquiredGeneration: Generation) {
        val cancelOperation = mutex.withLock {
            acquiredGeneration.waiterCount -= 1
            if (
                acquiredGeneration.waiterCount == 0 &&
                generation === acquiredGeneration &&
                !acquiredGeneration.operation.isCompleted
            ) {
                acquiredGeneration.operation.cancel()
                true
            } else {
                false
            }
        }
        if (cancelOperation) {
            acquiredGeneration.operation.join()
            mutex.withLock {
                if (generation === acquiredGeneration) generation = null
            }
        }
    }
}

private class OneShotControlledHardwareRefreshAborter(
    private val action: (Throwable) -> Unit,
) : ControlledHardwareRefreshAborter {
    private var aborted = false

    override fun abort(error: Throwable) {
        if (aborted) return
        aborted = true
        action(error)
    }
}

internal sealed interface HardwareStartupRecovery {
    data object ExplicitColdRecovery : HardwareStartupRecovery
    data class LiveRefresh(val state: MoneroSpentReconciliationState) : HardwareStartupRecovery
}

internal fun hardwareStartupRecovery(
    durableState: MoneroSpentReconciliationState,
): HardwareStartupRecovery =
    when (durableState) {
        MoneroSpentReconciliationState.ExplicitColdRecoveryPending ->
            HardwareStartupRecovery.ExplicitColdRecovery
        MoneroSpentReconciliationState.MigrationReplayRequired ->
            HardwareStartupRecovery.LiveRefresh(MoneroSpentReconciliationState.MigrationReplayPending)
        MoneroSpentReconciliationState.MigrationReplayPending ->
            HardwareStartupRecovery.LiveRefresh(durableState)
        else -> HardwareStartupRecovery.LiveRefresh(MoneroSpentReconciliationState.LiveRefreshPending)
    }

internal fun MoneroSpentReconciliationState.normalizedLiveRefreshState(): MoneroSpentReconciliationState =
    when (this) {
        MoneroSpentReconciliationState.MigrationReplayRequired,
        MoneroSpentReconciliationState.MigrationReplayPending ->
            MoneroSpentReconciliationState.MigrationReplayPending

        MoneroSpentReconciliationState.ExplicitColdRecoveryPending ->
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending

        else -> MoneroSpentReconciliationState.LiveRefreshPending
    }

internal fun MoneroSpentReconciliationState.requiresControlledRefreshFinalization(): Boolean =
    this != MoneroSpentReconciliationState.ExplicitColdRecoveryPending

internal suspend fun awaitControlledRefreshFinalization(
    finalization: Deferred<Unit>,
    timeoutMillis: Long,
) {
    try {
        withTimeout(timeoutMillis) {
            finalization.await()
        }
    } catch (_: TimeoutCancellationException) {
        throw HardwareWalletOperationException(
            HardwareWalletErrorCode.Network,
            "Timed out waiting for Monero controlled refresh finalization",
        )
    }
}

class MoneroKitWrapper(
    private val moneroWalletService: MoneroWalletService,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val account: Account,
    private val dispatcherProvider: DispatcherProvider,
    private val networkErrorTracker: NetworkErrorTracker,
    private val moneroTrezorGateway: MoneroTrezorOperationGateway,
    private val connectivityManager: IConnectivityManager,
    private val walletHealthReader: MoneroWalletHealthReader =
        ServiceMoneroWalletHealthReader(moneroWalletService),
) : MoneroWalletService.Observer {
    private val moneroFileDao: MoneroFileDao by inject(MoneroFileDao::class.java)
    private val moneroWalletUseCase: MoneroWalletUseCase by inject(MoneroWalletUseCase::class.java)
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase by inject(
        ValidateMoneroHeightUseCase::class.java
    )
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase by inject(
        RemoveMoneroWalletFilesUseCase::class.java
    )
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase by inject(
        IGetMoneroWalletFilesNameUseCase::class.java
    )
    private val logger = AppLogger("monero-kit").getScoped(account.id)
    @Volatile
    private var lastLoggedConnectionStatus: ConnectionStatus? = null
    private var lastLoggedSyncProgress: Int = -1

    @Volatile
    private var isStarted = false
    private var isPaused = false
    @Volatile
    private var startInProgress = false

    internal val isRestartableAfterFailedStart: Boolean
        get() = !isStarted && !startInProgress
    private var nativeStoreFaulted = false
    private var controlledLiveRefreshPending = false
    private var controlledLiveRefreshCommitted = false
    @Volatile
    private var hardwareRescanPending = false
    private var controlledRefreshFinalization: CompletableDeferred<Unit>? = null
    private var explicitColdRecoveryPending = false
    @Volatile
    private var keyImageSyncSession: Long? = null
    private val lifecycleMutex = Mutex()
    private val controlledRefreshCoordinator =
        ControlledHardwareRefreshCoordinator(CoroutineScope(SupervisorJob() + dispatcherProvider.io))
    private val spentStatusReconciler = MoneroSpentStatusReconciler(dispatcherProvider)
    private val spentStatusRecoveryOperations =
        object : MoneroSpentStatusRecoveryOperations<Wallet> {
            override fun isFullyHealthy(wallet: Wallet): Boolean =
                walletHealthReader.snapshot(wallet).isFullyHealthy

            override fun hasUnknownKeyImages(wallet: Wallet): Boolean =
                wallet.hasUnknownKeyImages()

            override suspend fun performPreservingRescan(
                wallet: Wallet,
                request: MoneroSpentStatusRescanRequest,
            ) {
                withPausedHardwareWallet(wallet, block = request::armAndRequest)
            }

            override fun requestPreservingRescan(wallet: Wallet) {
                wallet.rescanBlockchainAsyncPreserveKeyImages()
            }

            override suspend fun storeReconciledWallet(wallet: Wallet) {
                withPausedHardwareWallet(wallet, block = {
                    storeHardwareWallet(wallet, "Failed to store reconciled Monero wallet")
                })
            }

            override fun persistReady() {
                restoreSettingsManager.saveMoneroSpentReconciliationState(
                    account,
                    MoneroSpentReconciliationState.Ready,
                )
            }
        }
    private val spentStatusRecovery = MoneroSpentStatusRecovery(
        spentStatusReconciler,
        spentStatusRecoveryOperations,
    )
    private val hardwareAccount = account.type is AccountType.TrezorDevice
    val hardwareWallet: Boolean
        get() = hardwareAccount

    private val _spendReadiness = MutableStateFlow(
        if (hardwareAccount) MoneroSpendReadiness.Syncing else MoneroSpendReadiness.Ready,
    )
    val spendReadiness = _spendReadiness.asStateFlow()

    @Volatile
    private var storedForSync = false

    @Volatile
    private var nativeSyncState: AdapterState = AdapterState.Syncing()
    private val _syncState = MutableStateFlow<AdapterState>(AdapterState.Syncing())
    val syncState = _syncState.asStateFlow()

    private val _lastBlockInfoFlow = MutableStateFlow<LastBlockInfo?>(null)
    val lastBlockInfoFlow = _lastBlockInfoFlow.asStateFlow()
    private var cachedTotalHeight: Long = 0

    private var walletFileNameForStatus: String? = null

    private val _transactionsStateUpdatedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val transactionsStateUpdatedFlow = _transactionsStateUpdatedFlow.asSharedFlow()


    private suspend fun restoreFromBip39(
        account: Account,
        height: Long
    ) {
        logger.info("restoreFromBip39: height=$height")
        val accountType = account.type as? AccountType.Mnemonic
            ?: throw UnsupportedAccountException()
        val restoredAccount = moneroWalletUseCase.restoreFromBip39(
            accountType.words,
            accountType.passphrase,
            height
        ) ?: throw IllegalStateException("Failed to restore account from 12 words")
        moneroFileDao.insert(
            MoneroFileRecord(
                fileName = SecretString(restoredAccount.walletInnerName),
                password = SecretString(restoredAccount.password),
                accountId = account.id
            )
        )
    }

    suspend fun start(fixIfCorruptedFile: Boolean = true) = lifecycleMutex.withLock {
        startInternal(fixIfCorruptedFile)
    }

    private suspend fun startInternal(
        fixIfCorruptedFile: Boolean = true,
    ) =
        withContext(Dispatchers.IO) {
            if (!isStarted) {
                startInProgress = true
                logger.info("start: requested, fixIfCorruptedFile=$fixIfCorruptedFile, isStarted=$isStarted")
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                storedForSync = false
                // A previously synchronized hardware wallet must never retain its spend
                // authorization while a new native session is being opened.
                if (hardwareAccount) {
                    _spendReadiness.value = MoneroSpendReadiness.Syncing
                }
                publishSyncState(AdapterState.Connecting)
                try {
                    // Native start can synchronously invoke onWalletStarted().  Arm the current
                    // session before that call so the callback belongs to this start attempt.
                    activateReconciliationSession()
                    walletFileNameForStatus = null
                    val credentials = resolveWalletCredentials()
                    walletFileNameForStatus = credentials.fileName

                    val selectedNode = MoneroConfig.autoSelectNode()
                    if (selectedNode != null) {
                        logger.info("start: auto-selected node=$selectedNode")
                        WalletManager.getInstance()
                            .setDaemon(selectedNode)
                    } else {
                        logger.info("start: autoSelectNode returned null, set first default node")
                        WalletManager.getInstance()
                            .setDaemon(NodeInfo.fromString(DefaultNodes.entries.first().uri))
                    }

                    /*val walletFolder: File = Helper.getWalletRoot(App.instance)
                    val walletKeyFile = File(walletFolder, "$walletFileName.keys")
                    fixCorruptedWalletFile(walletKeyFile.absolutePath, walletPassword)*/

                    moneroWalletService.setObserver(this@MoneroKitWrapper)
                    logger.info("start: invoking startService for walletFileName=${credentials.fileName}")
                    startAccountService(
                        credentials.fileName,
                        credentials.password,
                        fixIfCorruptedFile,
                    )
                    isStarted = true
                    if (hardwareAccount && controlledLiveRefreshPending) {
                        awaitControlledHardwareRefreshFinalization()
                    } else if (hardwareAccount && explicitColdRecoveryPending) {
                        syncKeyImagesLocked()
                        explicitColdRecoveryPending = false
                    }
                    logger.info(
                        "start: completed startService, connection=${moneroWalletService.connectionStatus}, walletStatus=${moneroWalletService.wallet?.status}"
                    )
                    fixWalletHeight()
                } catch (e: Exception) {
                    handleStartFailure(e)
                } finally {
                    startInProgress = false
                }
            }
        }

    private suspend fun resolveWalletCredentials(): MoneroWalletCredentials =
        when (val accountType = account.type) {
            is AccountType.MnemonicMonero -> mnemonicMoneroCredentials(accountType)
            is AccountType.Mnemonic -> mnemonicCredentials()
            is AccountType.TrezorDevice -> trezorCredentials()
            else -> throw UnsupportedAccountException()
        }

    private suspend fun mnemonicMoneroCredentials(
        accountType: AccountType.MnemonicMonero,
    ): MoneroWalletCredentials {
        logger.info("start: using AccountType.MnemonicMonero")
        if (!Helper.getWalletFile(App.instance, accountType.walletInnerName).exists()) {
            Timber.d("Restoring Monero wallet from mnemonic...")
            logger.info("start: wallet file does not exist, restoring from mnemonic")
            moneroWalletUseCase.restore(
                words = accountType.words,
                height = getBirthdayHeight(account) ?: accountType.height,
                crazyPassExisting = accountType.password,
                walletInnerNameExisting = accountType.walletInnerName,
            )
        }
        return MoneroWalletCredentials(accountType.walletInnerName, accountType.password)
    }

    private suspend fun mnemonicCredentials(): MoneroWalletCredentials {
        logger.info("start: using AccountType.Mnemonic")
        if (moneroFileDao.getAssociatedRecord(account.id) == null) {
            logger.info("start: no associated wallet files, restoring from mnemonic")
            val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
            val height = restoreSettings.birthdayHeight
                ?: validateMoneroHeightUseCase.getTodayHeight()
            check(height != -1L) { "Monero restore height can't be -1" }
            restoreFromBip39(account = account, height = height)
        }
        val record = requireNotNull(moneroFileDao.getAssociatedRecord(accountId = account.id)) {
            "Account does not have a valid Monero file association"
        }
        return MoneroWalletCredentials(record.fileName.value, record.password.value)
    }

    private suspend fun trezorCredentials(): MoneroWalletCredentials {
        val record = requireNotNull(moneroFileDao.getAssociatedRecord(account.id)) {
            "Trezor account has no Monero wallet files"
        }
        return MoneroWalletCredentials(record.fileName.value, record.password.value)
    }

    internal suspend fun startAccountService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean,
    ) {
        if (hardwareAccount) {
            val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
            when (val recovery = hardwareStartupRecovery(durableState)) {
                HardwareStartupRecovery.ExplicitColdRecovery -> {
                    explicitColdRecoveryPending = true
                    moneroTrezorGateway.execute(account) {
                        startHardwareService(walletFileName, walletPassword)
                    }
                    return
                }

                is HardwareStartupRecovery.LiveRefresh -> {
                    val refreshState = recovery.state
                    restoreSettingsManager.saveMoneroSpentReconciliationState(account, refreshState)
                    controlledLiveRefreshPending = true
                    controlledLiveRefreshCommitted = false
                    moneroTrezorGateway.execute(account) {
                        try {
                            startHardwareServicePaused(walletFileName, walletPassword)
                            // A pending durable refresh has not established a safe native session.
                            if (durableState != MoneroSpentReconciliationState.LiveRefreshPending) {
                                refreshHardwareKeyImagesLeaseOwned(refreshState)
                                controlledLiveRefreshCommitted = true
                            }
                        } catch (error: Throwable) {
                            abortControlledHardwareWallet(error)
                            throw error
                        }
                    }
                    if (durableState == MoneroSpentReconciliationState.LiveRefreshPending) {
                        resumeInitialHardwareCatchUp()
                    }
                }
            }
        } else {
            startService(walletFileName, walletPassword, fixIfCorruptedFile)
        }
    }

    private fun handleStartFailure(error: Exception) {
        deactivateReconciliationSession()
        if (hardwareAccount) {
            val walletClosed = closeHardwareWalletAfterFailedStart(error)
            // The failed start cannot authorize reconciliation or spending, but a native wallet
            // that cleanup could not close must remain owned so a later stop() retries it.
            isStarted = !walletClosed
            isPaused = !walletClosed
            // A failed start is never a valid session, even if the native cleanup also failed.
            _spendReadiness.value = MoneroSpendReadiness.ReconciliationFailed
        }
        _syncState.value = AdapterState.NotSynced(error)
        logger.warning("start: failed with exception", error)
        Timber.e(error, "Failed to start Monero wallet")
        if (hardwareAccount) throw error
    }

    private fun closeHardwareWalletAfterFailedStart(operationError: Throwable): Boolean {
        val wallet = moneroWalletService.wallet ?: return true
        return try {
            val closed = moneroWalletService.stop(false)
            if (!closed) {
                operationError.addSuppressed(
                    wallet.status.toHardwareWalletCloseFailure(
                        "Failed to close Monero hardware wallet after start failure",
                    ),
                )
            }
            closed
        } catch (cleanupError: Throwable) {
            operationError.addSuppressed(cleanupError)
            false
        }
    }

    private fun startHardwareService(
        walletFileName: String,
        walletPassword: String,
    ) {
        val status = moneroWalletService.start(walletFileName, walletPassword)
        recordNativeConnectionError(status?.connectionStatus, status?.errorString)
        if (status?.isOk == true) return
        val error = status?.hardwareWalletError ?: if (
            status?.connectionStatus == ConnectionStatus.ConnectionStatus_Disconnected
        ) {
            HardwareWalletErrorCode.Network
        } else {
            HardwareWalletErrorCode.Protocol
        }
        throw HardwareWalletOperationException(error, status?.errorString)
    }

    private fun startHardwareServicePaused(
        walletFileName: String,
        walletPassword: String,
    ) {
        val status = moneroWalletService.startPaused(walletFileName, walletPassword)
        recordNativeConnectionError(status?.connectionStatus, status?.errorString)
        if (status?.isOk == true) return
        throw HardwareWalletOperationException(
            status?.hardwareWalletError ?: HardwareWalletErrorCode.Protocol,
            status?.errorString,
        )
    }

    private fun resumeInitialHardwareCatchUp() {
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        check(moneroWalletService.resume(this)) {
            "Failed to resume Monero wallet for initial synchronization"
        }
        isPaused = false
    }

    /** Runs while the lifecycle mutex and Trezor signer lease are both owned. */
    internal open fun refreshHardwareKeyImagesLeaseOwned(
        durableState: MoneroSpentReconciliationState,
        forcedMode: HardwareKeyImageRefreshResult.Mode? = null,
    ) {
        val wallet = requireNotNull(moneroWalletService.wallet) {
            "Monero hardware wallet is not initialized"
        }
        val restoreHeight = requireHardwareRestoreHeight()
        val pendingHeight = restoreSettingsManager.pendingMoneroRescanHeight(account)
        val mode = forcedMode ?: when {
            durableState == MoneroSpentReconciliationState.MigrationReplayPending ->
                HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight
            pendingHeight != null || wallet.restoreHeight != restoreHeight ->
                HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight
            else -> HardwareKeyImageRefreshResult.Mode.Continue
        }
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.savePendingMoneroRescan(account, restoreHeight)
        }
        wallet.refreshWithHardwareKeyImages(
            HardwareKeyImageRefreshResult.Request(mode, restoreHeight),
        )
        storeControlledHardwareRefresh(wallet, mode)
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
        }
        if (durableState == MoneroSpentReconciliationState.MigrationReplayPending) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                account,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
    }

    private fun requireHardwareRestoreHeight(): Long =
        requireNotNull(restoreSettingsManager.pendingMoneroRescanHeight(account)
            ?: restoreSettingsManager.settings(account, BlockchainType.Monero).birthdayHeight
            ?.takeIf { it >= 0 }) {
            "Monero hardware wallet has no restore height"
        }

    private fun storeControlledHardwareRefresh(
        wallet: Wallet,
        mode: HardwareKeyImageRefreshResult.Mode,
    ) {
        val status = when (mode) {
            HardwareKeyImageRefreshResult.Mode.Continue -> wallet.storeSafe()
            HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight -> wallet.storeWithKeysSafe()
        }
        if (status == MONERO_STORE_OK) return
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.StoreFailed,
            "Failed to store controlled Monero Live Refresh",
        )
        if (status == MONERO_STORE_NATIVE_FAULT) {
            abandonFaultedWallet(failure)
        }
        throw failure
    }

    private fun abortControlledHardwareWallet(error: Throwable) {
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        deactivateReconciliationSession()
        _syncState.value = AdapterState.NotSynced(error)
        try {
            if (!moneroWalletService.stop(false)) {
                moneroWalletService.abandonFaultedWallet()
            }
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
            moneroWalletService.abandonFaultedWallet()
        }
        isStarted = false
        isPaused = false
        _spendReadiness.value = MoneroSpendReadiness.NeedsKeyImageSync
    }

    private suspend fun startService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean
    ) {
        logger.info("startService: start walletFileName=$walletFileName fixIfCorruptedFile=$fixIfCorruptedFile")
        try {
            val walletStatus = moneroWalletService.start(walletFileName, walletPassword)
            logger.info(
                "startService: initial status=${walletStatus?.toString()} isOk=${walletStatus?.isOk} connection=${walletStatus?.connectionStatus}"
            )
            // Route EVERY start() result through the self-gated helper (incl. a successful Connected
            // start, which arms lastLoggedConnectionStatus so the first onRefreshed is not a "new" transition).
            recordNativeConnectionError(walletStatus?.connectionStatus, walletStatus?.errorString)
            if (walletStatus?.isOk != true) {
                logger.info("startService: wallet status not ok, scheduling retry")
                Timber.d("Monero wallet start error: $walletStatus, restarting")
                if (walletStatus?.connectionStatus == ConnectionStatus.ConnectionStatus_Disconnected) {
                    logger.info("startService: detected disconnected status, aborting start")
                    throw Exception("No internet connection")
                } else if (walletStatus == null) {
                    // Possible corrupted wallet file
                    getBirthdayHeight(account)?.let {
                        resetWalletAndRestart(it)
                    }
                } else {
                    retryStart(walletFileName, walletPassword)
                }
            }
        } catch (e: WalletCorruptedException) {
            logger.warning("startService: WalletCorruptedException received", e)
            try {
                if (fixIfCorruptedFile) {
                    if (e.message?.contains("std::bad_alloc") == true) { // too big cache file
                        val cacheFileSize = tryOrNull { getCacheFile().sizeInMb() } ?: ""
                        val deleted = tryOrNull { getCacheFile()?.delete() } ?: false
                        Timber.d("MoneroKitManager: detected bad_alloc error(size: $cacheFileSize), deleted cache file: $deleted")
                        logger.info("startService: detected bad_alloc error(size: $cacheFileSize), deleting cache file, deleted=$deleted")
                        startService(walletFileName, walletPassword, false)
                        return
                    }
                    Timber.e(
                        e,
                        "WalletCorruptedException, trying to fix wallet, cache size: ${tryOrNull { getCacheFile().sizeInMb() } ?: "unknown"}"
                    )
                    logger.info("startService: attempting wallet fix after corruption")
                    getBirthdayHeight(account)?.let {
                        resetWalletAndRestart(it)
                    }
                } else {
                    Timber.e(e, "WalletCorruptedException, fix disabled")
                    logger.info("startService: wallet fix disabled, corruption remains")
                }
            } catch (ex: Exception) {
                logger.warning("startService: failed while handling WalletCorruptedException", ex)
                Timber.e(ex, "Failed to fix corrupted wallet")
            }
        } catch (e: Exception) {
            logger.warning("startService: unexpected exception", e)
            throw e
        }
    }

    private suspend fun retryStart(walletFileName: String, walletPassword: String) {
        delay(3_000)
        val retryStatus = moneroWalletService.start(walletFileName, walletPassword)
        logger.info(
            "startService: retry status=${retryStatus?.toString()} isOk=${retryStatus?.isOk} connection=${retryStatus?.connectionStatus}"
        )
        recordNativeConnectionError(retryStatus?.connectionStatus, retryStatus?.errorString)
    }

    private fun getBirthdayHeight(account: Account): Long? {
        var birthdayHeight = restoreSettingsManager.settings(
            account,
            BlockchainType.Monero
        ).birthdayHeight
        if ((birthdayHeight ?: 0L) <= 0L) {
            birthdayHeight = (account.type as? AccountType.MnemonicMonero)?.height
        }
        return birthdayHeight
    }

    /**
     * @return true if wallet need to be fixed
     */
    private suspend fun fixCorruptedWalletFile(
        walletKeysFileName: String,
        walletPassword: String
    ) {
        logger.info("fixCorruptedWalletFile: check walletKeysFileName=$walletKeysFileName")
        if ((account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12) return

        if (WalletManager.getInstance()
                .verifyWalletPassword(walletKeysFileName, walletPassword, false)
        ) return

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        Timber.d("Fixing corrupted wallet file with height: ${restoreSettings.birthdayHeight}")
        logger.info("fixCorruptedWalletFile: fixing with restoreHeight=${restoreSettings.birthdayHeight}")
        restoreSettings.birthdayHeight?.let {
            resetWalletAndRestart(it)
        }
    }

    private suspend fun fixWalletHeight() {
        if (moneroWalletService.wallet?.restoreHeight != -1L ||
            (account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12
        ) return

        logger.info("fixWalletHeight: restoreHeight missing, resetting to validated height")
        // Use day of publishing this changes on google play as height
        // to fix possible first day of using this feature by users
        resetWalletAndRestart(validateMoneroHeightUseCase("2025-08-13"))
    }

    /**
     * Destructive rescan entry point exposed to callers that need to trigger it directly
     * (as opposed to the automatic corruption-recovery paths below, which call
     * [resetWalletAndRestart] from within a context that already holds [lifecycleMutex]).
     */
    suspend fun rescan(newHeight: Long) {
        if (hardwareAccount) {
            rescanHardwareWallet(newHeight)
        } else {
            lifecycleMutex.withLock {
                resetWalletAndRestart(newHeight)
            }
        }
    }

    private suspend fun rescanHardwareWallet(newHeight: Long) {
        require(newHeight >= 0) { "Monero restore height must be non-negative" }
        lifecycleMutex.withLock {
            restoreSettingsManager.savePendingMoneroRescan(account, newHeight)
        }
        hardwareRescanPending = true
        try {
            setSyncStateForSession(requireActiveReconciliationSession(), AdapterState.Syncing())
            refreshHardwareKeyImages(HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight)
        } finally {
            hardwareRescanPending = false
        }
    }

    suspend fun refreshHardwareKeyImages() {
        refreshHardwareKeyImages(forcedMode = null)
    }

    private suspend fun refreshHardwareKeyImages(
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ) {
        controlledRefreshCoordinator.execute(
            object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    lifecycleMutex.withLock {
                        try {
                            val requiresFinalization =
                                refreshHardwareKeyImagesLocked(aborter, forcedMode)
                            if (requiresFinalization) {
                                awaitControlledHardwareRefreshFinalization()
                            }
                        } catch (error: Throwable) {
                            // Keep abort and native close inside lifecycle serialization. A queued
                            // stop/restart must not touch this wallet until discard completes.
                            aborter.abort(error)
                            throw error
                        }
                    }
                }

                override fun abort(error: Throwable) {
                    abortControlledHardwareWallet(error)
                }
            },
        )
    }

    private suspend fun refreshHardwareKeyImagesLocked(
        aborter: ControlledHardwareRefreshAborter,
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ): Boolean {
        check(hardwareAccount) { "Monero Live Refresh requires a hardware wallet" }
        val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
        val requiresControlledFinalization =
            durableState.requiresControlledRefreshFinalization()
        if (!requiresControlledFinalization) {
            resumeExplicitColdRecoveryLocked()
            return false
        }
        val refreshState = durableState.normalizedLiveRefreshState()
        restoreSettingsManager.saveMoneroSpentReconciliationState(account, refreshState)
        controlledLiveRefreshPending = true
        controlledLiveRefreshCommitted = false
        val credentials = if (moneroWalletService.wallet == null) resolveWalletCredentials() else null
        moneroTrezorGateway.execute(account) {
            try {
                credentials?.let {
                    openPausedHardwareWalletForRefresh {
                        startHardwareServicePaused(it.fileName, it.password)
                    }
                }
                val wallet = requireNotNull(moneroWalletService.wallet) {
                    "Monero hardware wallet is not initialized"
                }
                if (!isPaused) {
                    pauseAndDrain(wallet)
                }
                refreshHardwareKeyImagesLeaseOwned(refreshState, forcedMode)
                controlledLiveRefreshCommitted = true
            } catch (error: Throwable) {
                aborter.abort(error)
                throw error
            }
        }
        return true
    }

    private suspend fun resumeExplicitColdRecoveryLocked() {
        val credentials = if (moneroWalletService.wallet == null) resolveWalletCredentials() else null
        credentials?.let {
            openPausedHardwareWalletForRefresh {
                startHardwareServicePaused(it.fileName, it.password)
            }
        }
        val wallet = requireNotNull(moneroWalletService.wallet) {
            "Monero hardware wallet is not initialized"
        }
        if (!isPaused) pauseAndDrain(wallet)
        syncKeyImagesLocked()
    }

    internal fun openPausedHardwareWalletForRefresh(start: () -> Unit) {
        val previousStartInProgress = startInProgress
        startInProgress = true
        try {
            activateReconciliationSession()
            start()
            isStarted = true
            isPaused = true
        } finally {
            startInProgress = previousStartInProgress
        }
    }

    private suspend fun resetWalletAndRestart(birthdayHeight: Long) {
        logger.info("resetWalletAndRestart: requested with birthdayHeight=$birthdayHeight")
        val fileName = requireMoneroWalletFileName()

        stopInternal(false)

        val removed = removeMoneroWalletFilesUseCase(fileName)
        if (!removed) {
            failRescanAfterRemoval(fileName)
        }

        moneroFileDao.deleteAssociatedRecord(account.id)

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        restoreSettings.birthdayHeight = birthdayHeight
        restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)

        startInternal(fixIfCorruptedFile = false)
        // startInternal swallows startup/derive failures into AdapterState.NotSynced and leaves
        // isStarted=false. Surface that as a hard rescan failure so callers don't report success
        // after the destructive reset failed to restart the wallet. (Transient "not synced yet"
        // still has isStarted=true, so a normal offline start is not treated as a failure.)
        if (!isStarted) {
            failRescanAfterRestart()
        }
        logger.info("resetWalletAndRestart: restart complete")
    }

    private suspend fun requireMoneroWalletFileName(): String =
        getMoneroWalletFilesNameUseCase(account)
            ?: throw MoneroRescanException("No Monero wallet file found for account ${account.id}")

    private suspend fun failRescanAfterRemoval(fileName: String): Nothing {
        logger.info("resetWalletAndRestart: failed to remove walletFile=$fileName, rolling back")
        startInternal(fixIfCorruptedFile = false)
        throw MoneroRescanException("Failed to remove Monero wallet file $fileName")
    }

    private fun failRescanAfterRestart(): Nothing {
        throw MoneroRescanException(
            "Failed to restart Monero wallet after rescan for account ${account.id}",
        )
    }

    suspend fun stop(saveWallet: Boolean = true) = lifecycleMutex.withLock {
        stopInternal(saveWallet)
    }

    internal suspend fun <T> withNativeWalletReleased(
        block: suspend () -> T,
    ): T = lifecycleMutex.withLock {
        val restart = isStarted
        if (restart) {
            withContext(NonCancellable) { stopInternal() }
        }
        val result = try {
            block()
        } catch (error: Throwable) {
            if (restart) {
                restoreNativeWalletFailure()?.let(error::addSuppressed)
            }
            throw error
        }
        if (restart) {
            restoreNativeWalletFailure()?.let { throw it }
        }
        result
    }

    private suspend fun restoreNativeWalletFailure(): Throwable? =
        try {
            withContext(NonCancellable) {
                startInternal()
                check(isStarted) { "Failed to restore the active Monero wallet" }
            }
            null
        } catch (error: Throwable) {
            error
        }

    private suspend fun stopInternal(saveWallet: Boolean = true) = withContext(Dispatchers.IO) {
        // Invalidate before the native close starts. A refresh callback can arrive while close()
        // is blocking, but it must not be able to attach reconciliation work to this session.
        deactivateReconciliationSession()
        try {
            if (isStarted) {
                logger.info("stop: stopping service saveWallet=$saveWallet")
                if (hardwareAccount) {
                    if (!moneroWalletService.stop(saveWallet)) {
                        isPaused = true
                        throw moneroWalletService.wallet?.status.toHardwareWalletCloseFailure(
                            "Failed to close Monero hardware wallet",
                        )
                    }
                } else {
                    moneroWalletService.stop(saveWallet)
                }
                isStarted = false
                isPaused = false
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                if (hardwareAccount) {
                    _spendReadiness.value = MoneroSpendReadiness.Syncing
                }
                logger.info("stop: service stopped")
            } else {
                logger.info("stop: skip, service already stopped")
            }
        } finally {
            // Drain a callback that was already in flight before the first cancellation.
            deactivateReconciliationSession()
        }
    }

    suspend fun pause() = lifecycleMutex.withLock {
        if (isStarted && !isPaused) {
            logger.info("pause: pausing wallet refresh")
            moneroWalletService.pause()
            isPaused = true
            logger.info("pause: done")
        } else {
            logger.info("pause: skip, isStarted=$isStarted isPaused=$isPaused")
        }
    }

    suspend fun resume(): Boolean = lifecycleMutex.withLock {
        resumeInternal()
    }

    private fun resumeInternal(): Boolean {
        if (isStarted && isPaused) {
            logger.info("resume: resuming wallet refresh")
            val resumed = moneroWalletService.resume(this)
            if (resumed) {
                isPaused = false
                logger.info("resume: done")
            } else {
                logger.info("resume: service resume returned false")
            }
            return resumed
        } else {
            logger.info("resume: skip, isStarted=$isStarted isPaused=$isPaused")
            return false
        }
    }

    /**
     * Persists the wallet cache via a SIGSEGV-guarded native store, once per session,
     * the first time sync reaches [AdapterState.Synced]. Runs OFF the refresh thread
     * (dispatcherProvider.io) and is serialized with stop()/pause()/resume()/send() via
     * lifecycleMutex, so it never races a concurrent native call on the wallet.
     */
    suspend fun saveSynced(): Boolean = lifecycleMutex.withLock {
        withContext(dispatcherProvider.io) {
            if (!shouldSaveSyncedWallet()) {
                return@withContext storedForSync
            }
            var status = -1
            try {
                moneroWalletService.pause()
                delay(200)
                var attempt = 0
                while (attempt < 2) {
                    status = moneroWalletService.wallet?.storeSafe() ?: -1
                    if (status != 1) break
                    attempt++
                    delay(200)
                }
                when (status) {
                    MONERO_STORE_OK -> {
                        if (hardwareAccount) {
                            restoreSettingsManager.clearPendingMoneroRescan(account)
                        }
                        storedForSync = true
                        logger.info("saveSynced: stored at height=${moneroWalletService.wallet?.blockChainHeight}")
                    }

                    MONERO_STORE_NATIVE_FAULT -> {
                        val failure = HardwareWalletOperationException(
                            HardwareWalletErrorCode.StoreFailed,
                            "Failed to store synchronized Monero wallet",
                        )
                        abandonFaultedWallet(failure)
                        logger.warning("saveSynced: SIGSEGV, wallet abandoned", failure)
                    }

                    else -> logger.info("saveSynced: storeSafe status=$status")
                }
            } finally {
                if (status == MONERO_STORE_NATIVE_FAULT) {
                    recoverFaultedWallet()?.let {
                        logger.warning("saveSynced: failed to reopen faulted wallet", it)
                    }
                } else {
                    moneroWalletService.resume(this@MoneroKitWrapper)
                }
            }
            storedForSync
        }
    }

    private fun shouldSaveSyncedWallet(): Boolean =
        isStarted &&
            !isPaused &&
            !storedForSync &&
            _syncState.value is AdapterState.Synced

    suspend fun refresh() = lifecycleMutex.withLock {
        if (_syncState.value is AdapterState.Syncing) {
            logger.info("refresh: skip, already syncing")
            Timber.d("MoneroKitWrapper: Already syncing, skipping refresh")
            return@withLock
        }
        try {
            if (isStarted) {
                refreshStartedWallet()
            } else {
                logger.info("refresh: starting wallet")
                startInternal()
            }
        } catch (e: Exception) {
            logger.warning("refresh: failed to refresh wallet", e)
            Timber.e(e, "Failed to refresh Monero wallet")
        }
    }

    private suspend fun refreshStartedWallet() {
        if (isPaused && !resumeInternal()) {
            logger.info("refresh: restarting wallet after failed resume")
            restartInternal()
            return
        }

        if (requestWalletRefresh()) {
            logger.info("refresh: requesting wallet refresh")
        } else {
            logger.info("refresh: restarting wallet")
            restartInternal()
        }
    }

    private suspend fun restartInternal() {
        stopInternal(saveWallet = false)
        startInternal()
    }

    private fun requestWalletRefresh(): Boolean {
        val wallet = moneroWalletService.wallet ?: return false
        wallet.refreshAsync()
        return true
    }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-send.
    suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): String = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            if (hardwareAccount) {
                // This intentionally touches no native wallet state: do it before pause/drain
                // and before acquiring a hardware-device session.
                requireHardwareSendLifecycleReady()
                val wallet = moneroWalletService.wallet
                    ?: throw IllegalStateException("Monero wallet not initialized")
                sendHardware(wallet, amount, address, memo)
            } else {
                val wallet = moneroWalletService.wallet
                    ?: throw IllegalStateException("Monero wallet not initialized")
                val txData = buildTxData(amount, address, memo, wallet)
                moneroWalletService.prepareTransaction(txData)
                moneroWalletService.sendTransaction(memo)
            }
        }
    }

    private suspend fun sendHardware(
        wallet: Wallet,
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): String {
        return withPausedHardwareWallet(
            wallet = wallet,
            block = {
                var prepared = false
                try {
                    moneroTrezorGateway.execute(account) {
                        requireHardwareSendReady(wallet)
                        moneroWalletService.prepareTransaction(buildTxData(amount, address, memo, wallet))
                        prepared = true
                    }
                    requireHardwareSendReady(wallet)
                    moneroWalletService.sendTransaction(memo)
                } catch (error: Throwable) {
                    if (prepared) {
                        try {
                            wallet.disposePendingTransaction()
                        } catch (cleanupError: Throwable) {
                            error.addSuppressed(cleanupError)
                        }
                    }
                    throw error
                }
            },
            preserveResultOnResumeFailure = true,
        )
    }

    suspend fun syncKeyImages() = lifecycleMutex.withLock {
        syncKeyImagesLocked()
    }

    private suspend fun syncKeyImagesLocked() {
        withContext(dispatcherProvider.io) {
            check(hardwareAccount) { "Key image sync requires a hardware wallet" }
            val session = requireActiveReconciliationSession()
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            keyImageSyncSession = session
            try {
                // This write is the crash boundary: a process death at any later point must make
                // the next start reconcile rather than exposing imported key images as spendable.
                restoreSettingsManager.saveMoneroSpentReconciliationState(
                    account,
                    MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
                )
                setSpendReadinessForSession(session, MoneroSpendReadiness.CheckingKeyImages)
                val result = withPausedHardwareWallet(wallet, block = {
                    moneroTrezorGateway.execute(account) {
                        wallet.coldKeyImageSync().also {
                            // Keep this inside the gateway operation.  The Trezor operation must
                            // not be released before the imported key images are durable.
                            storeHardwareWallet(
                                wallet,
                                "Failed to store synchronized Monero key images",
                            )
                        }
                    }
                })

                when (coldKeyImageSyncNextStep(result.spentStatusVerified, wallet.hasUnknownKeyImages())) {
                    ColdKeyImageSyncNextStep.TrustedReady -> finalizeTrustedKeyImageSync(session, wallet)
                    ColdKeyImageSyncNextStep.PreserveKeyImagesRescan -> requestSpentReconciliation(session, wallet)
                    ColdKeyImageSyncNextStep.NeedsKeyImageSync -> {
                        clearReconciliationOperation(session)
                        setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
                    }
                }
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
                throw error
            }
        }
    }

    suspend fun displayAddressOnDevice(addressIndex: Int) = lifecycleMutex.withLock {
        withContext(dispatcherProvider.io) {
            require(addressIndex >= 0) { "Monero address index must be non-negative" }
            check(hardwareAccount) { "Address display requires a hardware wallet" }
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            withPausedHardwareWallet(wallet, block = {
                moneroTrezorGateway.execute(account) {
                    wallet.deviceShowAddress(0, addressIndex, "")
                }
            })
        }
    }

    private fun pauseAndDrain(wallet: Wallet) {
        moneroWalletService.pause()
        isPaused = true
        try {
            check(wallet.pauseRefreshAndDrain()) { "Failed to drain Monero refresh" }
        } catch (error: Throwable) {
            if (!moneroWalletService.resume(this)) {
                error.addSuppressed(
                    IllegalStateException("Failed to resume Monero wallet after drain failure"),
                )
            } else {
                isPaused = false
            }
            throw error
        }
    }

    private fun storeHardwareWallet(wallet: Wallet, failureMessage: String) {
        storeMoneroWalletSafely(
            failureMessage = failureMessage,
            store = wallet::storeSafe,
            onNativeFault = ::abandonFaultedWallet,
        )
    }

    private fun abandonFaultedWallet(failure: HardwareWalletOperationException) {
        deactivateReconciliationSession()
        moneroWalletService.abandonFaultedWallet()
        isStarted = false
        isPaused = false
        nativeStoreFaulted = true
        _syncState.value = AdapterState.NotSynced(failure)
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private suspend fun recoverFaultedWallet(): Throwable? {
        nativeStoreFaulted = false
        return try {
            startInternal(fixIfCorruptedFile = true)
            null
        } catch (error: Throwable) {
            error
        }
    }

    private fun resumeAfterHardwareOperation() {
        if (!isStarted) return
        check(moneroWalletService.resume(this)) {
            "Failed to resume Monero wallet after hardware operation"
        }
        isPaused = false
    }

    private suspend fun awaitControlledHardwareRefreshFinalization() {
        val finalization = CompletableDeferred<Unit>()
        check(controlledRefreshFinalization == null) {
            "Monero controlled refresh finalization is already pending"
        }
        controlledRefreshFinalization = finalization
        try {
            check(moneroWalletService.resumeAfterControlledRefresh(this)) {
                "Failed to resume Monero wallet after controlled refresh"
            }
            awaitControlledRefreshFinalization(
                finalization,
                CONTROLLED_REFRESH_FINALIZATION_TIMEOUT_MILLIS,
            )
        } finally {
            if (controlledRefreshFinalization === finalization) {
                controlledRefreshFinalization = null
            }
        }
        isPaused = false
    }

    private suspend fun <T> withPausedHardwareWallet(
        wallet: Wallet,
        block: suspend () -> T,
        preserveResultOnResumeFailure: Boolean = false,
    ): T {
        try {
            pauseAndDrain(wallet)
        } catch (error: Throwable) {
            failHardwarePause(error)
        }
        val result = try {
            block()
        } catch (error: Throwable) {
            failHardwareOperation(error)
        }
        return completeHardwareOperation(
            result,
            resumeHardwareWalletFailure(),
            preserveResultOnResumeFailure,
        )
    }

    internal fun <T> completeHardwareOperation(
        result: T,
        resumeFailure: Throwable?,
        preserveResultOnResumeFailure: Boolean,
    ): T {
        resumeFailure?.let { handleHardwareResumeFailure(it, preserveResultOnResumeFailure) }
        return result
    }

    private fun failHardwarePause(error: Throwable): Nothing {
        if (isPaused) {
            recordHardwareRecoveryFailure(error)
        }
        throw error
    }

    private suspend fun failHardwareOperation(error: Throwable): Nothing {
        if (nativeStoreFaulted) {
            if (startInProgress) {
                nativeStoreFaulted = false
            } else {
                withContext(NonCancellable) {
                    recoverFaultedWallet()
                }?.let(error::addSuppressed)
            }
            throw error
        }
        resumeHardwareWalletFailure()?.let { resumeFailure ->
            recordHardwareRecoveryFailure(resumeFailure)
            error.addSuppressed(resumeFailure)
        }
        throw error
    }

    private fun handleHardwareResumeFailure(
        error: Throwable,
        preserveResult: Boolean,
    ) {
        recordHardwareRecoveryFailure(error)
        if (!preserveResult) {
            throw error
        }
    }

    private fun recordHardwareRecoveryFailure(error: Throwable) {
        _syncState.value = AdapterState.NotSynced(error)
        _spendReadiness.value = MoneroSpendReadiness.Syncing
        Timber.e(error, "Failed to recover Monero wallet after hardware operation")
    }

    private fun resumeHardwareWalletFailure(): Throwable? =
        try {
            resumeAfterHardwareOperation()
            null
        } catch (error: Throwable) {
            error
        }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun createSignedRawTransaction(
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): SignedRawMoneroTransaction = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            check(!hardwareAccount) { "Offline signing is unavailable for Trezor Monero wallets" }
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            moneroWalletService.createSignedRawTransaction(buildTxData(amount, address, memo, wallet))
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun submitSignedRawTransaction(raw: ByteArray): RawMoneroBroadcastResult =
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                if (hardwareAccount) {
                    requireHardwareSendLifecycleReady()
                    val wallet = moneroWalletService.wallet
                        ?: throw IllegalStateException("Monero wallet not initialized")
                    withPausedHardwareWallet(
                        wallet = wallet,
                        block = {
                            requireHardwareSendReady(wallet, requireCurrentWallet = true)
                            moneroWalletService.submitSignedRawTransaction(raw)
                        },
                        preserveResultOnResumeFailure = true,
                    )
                } else {
                    moneroWalletService.submitSignedRawTransaction(raw)
                }
            }
        }

    /**
     * This is deliberately called inside [lifecycleMutex] at each hardware transaction boundary.
     * Reading all state together makes a stale Ready value insufficient to authorize spending.
     */
    private fun requireHardwareSendLifecycleReady() {
        check(isStarted && activeReconciliationSession() != null) {
            "Monero hardware wallet session is not active"
        }
        check(
            isHardwareSpendLifecycleReady(
                syncState = _syncState.value,
                durableState = restoreSettingsManager.moneroSpentReconciliationState(account),
                spendReadiness = _spendReadiness.value,
            ),
        ) { "Monero hardware wallet is not ready to send" }
    }

    private fun requireNoUnknownHardwareKeyImages(wallet: Wallet) {
        check(!wallet.hasUnknownKeyImages()) { "Monero hardware wallet has unknown key images" }
    }

    private fun requireHardwareSendReady(wallet: Wallet, requireCurrentWallet: Boolean = false) {
        requireHardwareSendLifecycleReady()
        if (requireCurrentWallet) {
            check(moneroWalletService.wallet === wallet) { "Monero hardware wallet changed before sending" }
        }
        check(walletHealthReader.snapshot(wallet).isFullyHealthy) {
            "Monero hardware wallet native health changed before sending"
        }
        // Keep native checks at the transaction boundary, after JVM-only gates and health read.
        requireNoUnknownHardwareKeyImages(wallet)
    }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-estimate.
    suspend fun estimateFee(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): Long = lifecycleMutex.withLock {
        val wallet = moneroWalletService.wallet
            ?: throw IllegalStateException("Monero wallet not initialized")
        val txData = buildTxData(amount, address, memo, wallet)
        val fee = wallet.estimateTransactionFee(txData)
        if (fee < 0) {
            throw IllegalStateException("Failed to estimate fee: wallet not synced with daemon")
        }
        fee
    }

    private fun buildTxData(
        amount: BigDecimal,
        address: String,
        memo: String?,
        wallet: Wallet
    ) = TxData().apply {
        this.destination = address
        this.amount = amount.movePointRight(MoneroAdapter.decimal).toLong()
        this.mixin = wallet.defaultMixin
        this.priority = PendingTransaction.Priority.Priority_Default
        memo?.let {
            this.userNotes = UserNotes(it)
        }
    }

    fun statusInfo(): Map<String, Any> {
        logger.info(
            "statusInfo: connection=${moneroWalletService.connectionStatus} wallet=${moneroWalletService.wallet?.status} isStarted=$isStarted restoreHeight=${moneroWalletService.wallet?.restoreHeight}"
        )
        val base = mapOf<String, Any>(
            "connectionStatus" to moneroWalletService.connectionStatus,
            "walletStatus" to moneroWalletService.wallet?.status?.toString().orEmpty(),
            "isStarted" to isStarted,
            "Birthday Height" to (getBirthdayHeight(account) ?: "Not set"),
            "Cache file size" to (tryOrNull { getCacheFile().sizeInMb() } ?: "")
        )
        return networkErrorTracker.appendNetworkErrors(base, BlockchainType.Monero, account.id)
    }

    /**
     * Records a native (non-OkHttp) connection error into the shared tracker. Self-gated on
     * [lastLoggedConnectionStatus] so repeated same-status callbacks record once; ignores null and
     * Connected. Passive: never throws into the caller.
     */
    private fun recordNativeConnectionError(status: ConnectionStatus?, errorString: String?) {
        val s = status ?: return
        if (s == lastLoggedConnectionStatus) return
        lastLoggedConnectionStatus = s
        if (s == ConnectionStatus.ConnectionStatus_Connected) return
        tryOrNull {
            val addr = tryOrNull { WalletManager.getInstance().getDaemonAddress() }.orEmpty()
            networkErrorTracker.record(
                BlockchainType.Monero,
                account.id,
                NetworkErrorInfo(
                    source = "Monero",
                    method = s.name,
                    url = addr,
                    host = addr,
                    resolvedIps = emptyList(),
                    throwable = IllegalStateException(
                        errorString?.takeIf { it.isNotBlank() } ?: "Not connected"
                    ),
                )
            )
        }
    }

    private fun getCacheFile(): File? {
        return walletFileNameForStatus?.let { Helper.getWalletFile(App.instance, it) }
    }

    // Add methods for balance, transactions, etc.
    fun getBalance(): Long {
        return try {
            Timber.d("getBalance: ${moneroWalletService.wallet?.balance}")
            moneroWalletService.wallet?.balance ?: 0L
        } catch (e: Exception) {
            logger.warning("getBalance: failed to fetch balance", e)
            Timber.d("getBalance: Failed to get balance")
            0L
        }
    }

    fun getUnlockedBalance(): Long {
        return try {
            moneroWalletService.wallet?.unlockedBalance ?: 0L
        } catch (e: Exception) {
            logger.warning("getUnlockedBalance: failed to fetch unlocked balance", e)
            0L
        }
    }

    fun getAddress(): String {
        return try {
            requireNotNull(moneroWalletService.wallet).address
        } catch (e: Exception) {
            logger.warning("getAddress: failed to fetch address", e)
            Timber.d("getAddress: Failed to get address $e")
            ""
        }
    }

    suspend fun getSubaddresses(): List<MoneroSubaddressInfo> = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet ?: return@withContext emptyList()
            val count = wallet.numSubaddresses
            (0 until count).map { index ->
                val subaddress = wallet.getSubaddressObject(index)
                MoneroSubaddressInfo(
                    index = subaddress.addressIndex,
                    address = subaddress.address,
                    receivedAmount = subaddress.amount
                )
            }
        }
    }

    suspend fun createNewSubaddress(): String = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            wallet.newSubaddress
        }
    }

    fun getTransactions(): List<TransactionInfo> {
        if (!isStarted) return emptyList()
        return try {
            var transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
            if (transactions.isEmpty()) {
                moneroWalletService.wallet?.let {
                    moneroWalletService.wallet?.history?.refreshWithNotes(it)
                    transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
                }
            }
            transactions
        } catch (e: Exception) {
            logger.warning("getTransactions: failed to fetch transactions", e)
            Timber.d("getTransactions: Failed to get transactions")
            emptyList()
        }
    }

    override fun onRefreshed(
        wallet: Wallet?,
        full: Boolean
    ): Boolean {
        val session = activeReconciliationSession() ?: return false
        if (!isStarted) return false
        val health = try {
            walletHealthReader.snapshot(wallet)
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(session, error)
            return isRefreshCallbackFullyHandled(
                hardwareAccount,
                hasActiveReconciliationOperation(session),
                _spendReadiness.value,
            )
        }

        // A callback can arrive from a wallet that belonged to an earlier native session. It has
        // no authority over the active session, including its reconciliation operation.
        if (!health.callbackWalletIsCurrent) return false

        if (
            health.nativeConnectionStatus != lastLoggedConnectionStatus ||
            !health.nativeStatusError.isNullOrBlank()
        ) {
            logger.info(
                "onRefreshed: nativeConnection=${health.nativeConnectionStatus} full=$full " +
                    "current=${health.callbackWalletIsCurrent} statusOk=${health.nativeStatusIsOk} " +
                    "statusError=${health.nativeStatusError} " +
                    "serviceConnected=${health.serviceConnectionIsConnected} " +
                    "nativeConnected=${health.nativeConnectionIsConnected} " +
                    "isSynchronized=${health.walletIsSynchronized}"
            )
        }
        recordNativeConnectionError(health.nativeConnectionStatus, health.nativeStatusError)

        if (isRetryablePreservingRescanNativeStatusFailure(session, health)) {
            lastLoggedSyncProgress = -1
            setSyncStateForSession(session, AdapterState.Syncing())
            setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
            return false
        }

        if (hasMoneroNativeHealthFailure(health)) {
            val consumedAwaitingCallback = consumeFailedAwaitingReconciliationCallback(session)
            if (consumedAwaitingCallback || hasKeyImageSyncBegun(session)) {
                failClosedAfterReconciliationError(
                    session,
                    nativeHealthFailureError(health),
                )
            }
            lastLoggedSyncProgress = -1
            setSyncStateForSession(
                session,
                AdapterState.NotSynced(nativeHealthFailureError(health)),
            )
            updateHardwareReadiness(session, wallet, health)
            return !consumedAwaitingCallback && isRefreshCallbackFullyHandled(
                hardwareAccount = hardwareAccount,
                hasActiveReconciliationOperation = hasActiveReconciliationOperation(session),
                spendReadiness = _spendReadiness.value,
            )
        }

        val nativeConnected =
            health.nativeConnectionIsConnected && health.serviceConnectionIsConnected
        if (nativeConnected) {
            setLastBlockInfoForSession(
                session,
                if (moneroWalletService.daemonHeight != 0L) {
                    LastBlockInfo(moneroWalletService.daemonHeight.toInt())
                } else {
                    null
                },
            )
            emitTransactionsStateUpdatedForSession(session)
        }

        val isSynchronized = health.isFullyHealthy
        val currentHeight = if (nativeConnected) {
            tryOrNull { wallet?.blockChainHeight } ?: 0
        } else {
            0
        }
        // Manager-level height is a daemon RPC and nativeConnected lies while offline, so the device
        // network state gates it too — otherwise losing network mid-sync blocks the native callback.
        val totalHeight = if (nativeConnected && !isSynchronized && connectivityManager.isConnected.value) {
            tryOrNull { WalletManager.getInstance().blockchainHeight } ?: 0L
        } else {
            0L
        }

        if (shouldUpdateNativeSyncState()) {
            setNativeSyncStateForSession(
                session,
                resolveSyncState(nativeConnected, isSynchronized, currentHeight, totalHeight),
            )
        }
        updateHardwareReadiness(session, wallet, health)
        publishNativeSyncStateForSession(session)
        return isRefreshCallbackFullyHandled(
            hardwareAccount = hardwareAccount,
            hasActiveReconciliationOperation = hasActiveReconciliationOperation(session),
            spendReadiness = _spendReadiness.value,
        )
    }

    private fun shouldUpdateNativeSyncState(): Boolean =
        controlledLiveRefreshCommitted || !hardwareRescanPending && !controlledLiveRefreshPending

    private fun notConnectedState() = AdapterState.NotSynced(IllegalStateException("Not connected"))

    fun onNetworkLost() {
        publishSyncState(notConnectedState())
    }

    // `update` and not a plain assignment: the network can drop between computing a state and
    // storing it, and the CAS retry re-reads isConnected instead of overwriting NotSynced.
    private fun publishSyncState(state: AdapterState) {
        _syncState.update { if (connectivityManager.isConnected.value) state else notConnectedState() }
    }

    private fun resolveSyncState(
        nativeConnected: Boolean,
        isSynchronized: Boolean,
        currentHeight: Long,
        totalHeight: Long,
    ): AdapterState = when {
        !nativeConnected -> {
            Timber.d("MoneroKitWrapper: Not connected")
            logger.info("onRefreshed: connection lost, setting state=NotSynced")
            lastLoggedSyncProgress = -1
            notConnectedState()
        }

        isSynchronized -> {
            Timber.d("MoneroKitWrapper: Synced")
            logger.info("onRefreshed: wallet synchronized at height=$currentHeight")
            lastLoggedSyncProgress = 100
            AdapterState.Synced
        }

        else -> syncingState(currentHeight, totalHeight)
    }

    private fun syncingState(currentHeight: Long, totalHeight: Long): AdapterState {
        Timber.d("MoneroKitWrapper: Sync in progress")
        if (totalHeight > 0) {
            cachedTotalHeight = totalHeight
        }
        val heightToUse = if (totalHeight > 0) totalHeight else cachedTotalHeight
        Timber.d("currentHeight = $currentHeight, totalHeight = $totalHeight")

        val progressPercent = if (heightToUse > 0) {
            ((currentHeight.toDouble() / heightToUse) * 100).coerceIn(0.0, 100.0).toInt()
        } else {
            0
        }
        val blocksRemained = if (heightToUse > 0 && currentHeight < heightToUse) {
            heightToUse - currentHeight
        } else {
            null
        }
        logSyncProgress(progressPercent, blocksRemained)

        return AdapterState.Syncing(
            progress = progressPercent.toDouble(),
            blocksRemained = blocksRemained
        )
    }

    private fun logSyncProgress(progressPercent: Int, blocksRemained: Long?) {
        if (progressPercent == 0 && lastLoggedSyncProgress != 0) {
            logger.info("onRefreshed: sync progress started (0%)")
        }
        if (progressPercent >= 100 && lastLoggedSyncProgress < 100) {
            logger.info("onRefreshed: sync progress reached 100%")
        }
        if (progressPercent - lastLoggedSyncProgress >= 5 && progressPercent in 1..99) {
            logger.info("onRefreshed: sync progress=${progressPercent}% blocksRemained=$blocksRemained")
        }
        lastLoggedSyncProgress = progressPercent
    }

    override fun onProgress(text: String?) {
        if (!text.isNullOrBlank()) {
            logger.info("onProgress(text): $text")
        }
        Timber.d("onProgress: $text")
    }

    override fun onProgress(n: Int) {
        if (n % 10 == 0) {
            logger.info("onProgress(value): $n")
        }
        Timber.d("onProgress: $n")
    }

    override fun onWalletStarted(walletStatus: Wallet.Status?) {
        val session = activeReconciliationSession()
        if (!canAcceptWalletStartedCallback(session != null, isStarted, startInProgress)) return
        val activeSession = session ?: return

        val wallet = moneroWalletService.wallet
        val health = try {
            walletHealthReader.snapshot(wallet)
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(activeSession, error)
            return
        }
        logger.info("onWalletStarted: $health")
        Timber.d("onWalletStarted: native health=$health")
        val syncState = walletStartedSyncState(health)
        if (syncState is AdapterState.Synced) {
            Timber.d("MoneroKitWrapper: Synced")
        }
        setNativeSyncStateForSession(activeSession, syncState)
        updateHardwareReadiness(activeSession, wallet, health)
        publishNativeSyncStateForSession(activeSession)
    }

    private fun updateHardwareReadiness(
        session: Long,
        wallet: Wallet?,
        health: MoneroWalletHealthSnapshot,
    ) {
        if (!hardwareAccount || !isActiveReconciliationSession(session)) return
        if (explicitColdRecoveryPending) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.CheckingKeyImages)
            return
        }
        if (controlledLiveRefreshPending) {
            finalizeControlledHardwareRefresh(session, wallet, health)
            return
        }
        // A reconciliation failure is terminal for this native-wallet session.  In particular,
        // do not let later generic refresh callbacks restart recovery; only an explicit key-image
        // sync (or a new wallet lifecycle) may replace this retryable failure state.
        try {
            val hasUnknownKeyImages = if (canReadHardwareKeyImages(health)) {
                health.hasUnknownKeyImages ?: wallet?.hasUnknownKeyImages()
            } else {
                null
            }
            if (applyHardwareReadinessSnapshot(session, health, hasUnknownKeyImages)) return

            val generation = reconciliationAwaitingCallbackGeneration(session)
            if (generation != null) {
                // Only the callback for an operation that we armed may complete the durable
                // transition. Generic/stale synchronized callbacks cannot.
                finalizeReconciliationFromCallback(session, generation, checkNotNull(wallet), health)
            } else {
                schedulePendingReconciliation(session)
            }
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(session, error)
        }
    }

    private fun canReadHardwareKeyImages(health: MoneroWalletHealthSnapshot): Boolean =
        health.isFullyHealthy && nativeSyncState is AdapterState.Synced

    private fun finalizeControlledHardwareRefresh(
        session: Long,
        wallet: Wallet?,
        health: MoneroWalletHealthSnapshot,
    ) {
        try {
            if (!hasHealthyControlledRefresh(wallet, health)) {
                setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
                controlledRefreshFinalization?.completeExceptionally(
                    IllegalStateException("Monero controlled refresh did not complete with a healthy wallet"),
                )
                return
            }
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                account,
                MoneroSpentReconciliationState.Ready,
            )
            controlledLiveRefreshPending = false
            controlledLiveRefreshCommitted = false
            setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
            controlledRefreshFinalization?.complete(Unit)
        } catch (error: Throwable) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
            controlledRefreshFinalization?.completeExceptionally(
                error,
            )
        }
    }

    private fun hasHealthyControlledRefresh(
        wallet: Wallet?,
        health: MoneroWalletHealthSnapshot,
    ): Boolean {
        if (!controlledLiveRefreshCommitted) return false
        if (!health.isFullyHealthy) return false
        return wallet?.hasUnknownKeyImages() == false
    }

    /** Production callback readiness routing, kept JNI-free for deterministic manager tests. */
    internal fun applyHardwareReadinessSnapshot(
        session: Long,
        health: MoneroWalletHealthSnapshot,
        hasUnknownKeyImages: Boolean?,
    ): Boolean {
        if (!hardwareAccount || !isActiveReconciliationSession(session)) return true
        if (_spendReadiness.value == MoneroSpendReadiness.ReconciliationFailed) return true
        if (nativeSyncState !is AdapterState.Synced || !health.isFullyHealthy ||
            hasUnknownKeyImages == null
        ) {
            val awaitingExpectedRescan =
                reconciliationAwaitingCallbackGeneration(session) != null &&
                    !hasMoneroNativeHealthFailure(health)
            if (hasKeyImageSyncBegun(session) && !awaitingExpectedRescan) {
                failClosedAfterReconciliationError(
                    session,
                    IllegalStateException("Monero spent-status readiness is no longer healthy"),
                )
            } else {
                setSpendReadinessForSession(
                    session,
                    if (hasActiveReconciliationOperation(session)) {
                        MoneroSpendReadiness.ReconcilingSpentStatus
                    } else {
                        MoneroSpendReadiness.Syncing
                    },
                )
            }
            return true
        }
        if (hasUnknownKeyImages) {
            clearReconciliationOperation(session)
            setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
            return true
        }

        val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
        if (durableState == MoneroSpentReconciliationState.Ready) {
            setSpendReadinessForSession(
                session,
                if (canExposeMoneroSpendReady(true, health, false, durableState)) {
                    MoneroSpendReadiness.Ready
                } else {
                    MoneroSpendReadiness.ReconcilingSpentStatus
                },
            )
            return true
        }
        return false
    }

    private fun finalizeTrustedKeyImageSync(session: Long, wallet: Wallet) {
        try {
            when (
                trustedKeyImageSyncFinalizationOutcome(
                    health = walletHealthReader.snapshot(wallet),
                    hasUnknownKeyImages = wallet.hasUnknownKeyImages(),
                    syncState = nativeSyncState,
                )
            ) {
                TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync -> {
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
                }
                TrustedKeyImageSyncFinalizationOutcome.Ready -> {
                    restoreSettingsManager.saveMoneroSpentReconciliationState(
                        account,
                        MoneroSpentReconciliationState.Ready,
                    )
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
                }
                TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed ->
                    failClosedAfterReconciliationError(
                        session,
                        IllegalStateException("Trusted Monero key-image sync finalization is not ready"),
                    )
            }
        } catch (error: Throwable) {
            failClosedAfterReconciliationError(session, error)
            throw error
        }
    }

    /**
     * Starts the non-destructive daemon spent-status query after the Trezor operation has been
     * released. The second pause/drain prevents a pre-existing generic refresh callback from
     * being mistaken for this request's completion.
     */
    private suspend fun requestSpentReconciliation(session: Long, wallet: Wallet) {
        if (!isActiveReconciliationSession(session)) return
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        setSyncStateForSession(session, AdapterState.Syncing())
        try {
            applySpentStatusRequestResult(session, spentStatusRecovery.request(session, wallet))
        } catch (error: Throwable) {
            failClosedAfterReconciliationError(session, error)
            throw error
        }
    }

    /** JNI-free terminal wiring for spent-status request results. */
    internal fun applySpentStatusRequestResult(
        session: Long,
        result: MoneroSpentStatusRequestResult,
    ) {
        when (result) {
            MoneroSpentStatusRequestResult.Retry -> failClosedAfterReconciliationError(
                session,
                IllegalStateException("Monero wallet is unhealthy before spent-status reconciliation"),
            )
            MoneroSpentStatusRequestResult.NeedsKeyImageSync -> {
                clearReconciliationOperation(session)
                setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
            }
            MoneroSpentStatusRequestResult.AwaitingCallback -> Unit
        }
    }

    /** Recovers legacy/absent and PENDING records after ordinary synchronization, no Trezor needed. */
    private fun schedulePendingReconciliation(session: Long) {
        val generation = spentStatusReconciler.beginRecovery(session) ?: return
        val expectedPhase = MoneroReconciliationPhase.RecoveryRequested(generation)
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        launchReconciliation(session, expectedPhase) {
            val wallet = moneroWalletService.wallet
            try {
                if (wallet == null) {
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
                } else {
                    requestSpentReconciliation(session, wallet)
                }
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
            }
        }
    }

    private fun finalizeReconciliationFromCallback(
        session: Long,
        generation: Long,
        wallet: Wallet,
        health: MoneroWalletHealthSnapshot,
    ) {
        val callbackIsSuccessful = canFinalizeSpentReconciliation(health)
        val callbackDisposition = spentStatusRecovery.acceptCallback(
            session = session,
            generation = generation,
            callbackIsSuccessful = callbackIsSuccessful,
        )
        if (callbackDisposition == ReconciliationCallbackDisposition.Ignore) return
        if (callbackDisposition == ReconciliationCallbackDisposition.FailClosed) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
            return
        }
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        launchReconciliation(
            session = session,
            expectedPhase = MoneroReconciliationPhase.Finalizing(generation),
            expectedWallet = wallet,
        ) {
            try {
                spentStatusRecovery.finalizeAcceptedCallback(session, wallet)
                setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
            }
        }
    }

    private fun failClosedAfterReconciliationError(session: Long, error: Throwable) {
        clearReconciliationOperation(session)
        // Do not inspect JNI state while failing closed: the operation may have failed because
        // the callback/session was stale or native health had already become invalid.
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconciliationFailed)
        Timber.e(error, "Monero spent-status reconciliation failed")
    }

    private fun failClosedAfterPostSyncError(session: Long, error: Throwable) {
        failClosedAfterReconciliationError(session, error)
    }

    private fun hasKeyImageSyncBegun(session: Long): Boolean = keyImageSyncSession == session

    private fun reconciliationAwaitingCallbackGeneration(session: Long): Long? =
        spentStatusReconciler.awaitingCallbackGeneration(session)

    private fun hasActiveReconciliationOperation(session: Long): Boolean =
        spentStatusReconciler.hasActiveOperation(session)

    private fun clearReconciliationOperation(session: Long) {
        spentStatusReconciler.clearOperation(session)
    }

    private fun consumeFailedAwaitingReconciliationCallback(session: Long): Boolean =
        spentStatusReconciler.consumeFailedAwaitingCallback(session)

    private fun isRetryablePreservingRescanNativeStatusFailure(
        session: Long,
        health: MoneroWalletHealthSnapshot,
    ): Boolean =
        reconciliationAwaitingCallbackGeneration(session) != null &&
            hasConnectedNativeStatusFailure(health)

    private fun hasConnectedNativeStatusFailure(health: MoneroWalletHealthSnapshot): Boolean =
        !health.nativeStatusIsOk &&
            health.nativeConnectionIsConnected &&
            health.serviceConnectionIsConnected

    private fun activeReconciliationSession(): Long? = spentStatusReconciler.activeSession()

    private fun requireActiveReconciliationSession(): Long =
        checkNotNull(spentStatusReconciler.activeSession()) {
            "Monero reconciliation session is not active"
        }

    private fun isActiveReconciliationSession(session: Long): Boolean =
        spentStatusReconciler.isActive(session)

    private fun setSpendReadinessForSession(
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        if (!spentStatusReconciler.isActive(session)) return
        _spendReadiness.value = readiness
        if (readiness.isTerminalKeyImageSyncOutcome) {
            clearKeyImageSyncSession(session)
        }
        if (!spentStatusReconciler.isActive(session)) {
            _spendReadiness.compareAndSet(readiness, MoneroSpendReadiness.Syncing)
        } else if (readiness.isTerminalKeyImageSyncOutcome) {
            publishNativeSyncStateForSession(session)
        }
    }

    private fun clearKeyImageSyncSession(session: Long) {
        if (keyImageSyncSession == session) {
            keyImageSyncSession = null
        }
    }

    private fun setSyncStateForSession(session: Long, state: AdapterState) {
        setNativeSyncStateForSession(session, state)
        publishNativeSyncStateForSession(session)
    }

    private fun setNativeSyncStateForSession(session: Long, state: AdapterState) {
        if (spentStatusReconciler.isActive(session)) {
            nativeSyncState = state
        }
    }

    private fun publishNativeSyncStateForSession(session: Long) {
        _syncState.update { currentState ->
            if (!spentStatusReconciler.isActive(session)) {
                currentState
            } else if (connectivityManager.isConnected.value) {
                visibleSyncState(nativeSyncState)
            } else {
                notConnectedState()
            }
        }
    }

    private fun visibleSyncState(nativeState: AdapterState): AdapterState =
        if (
            nativeState is AdapterState.Synced &&
            _spendReadiness.value == MoneroSpendReadiness.ReconcilingSpentStatus
        ) {
            AdapterState.Syncing()
        } else {
            nativeState
        }

    private fun setLastBlockInfoForSession(session: Long, lastBlockInfo: LastBlockInfo?) {
        if (spentStatusReconciler.isActive(session)) {
            _lastBlockInfoFlow.value = lastBlockInfo
        }
    }

    private fun emitTransactionsStateUpdatedForSession(session: Long) {
        if (spentStatusReconciler.isActive(session)) {
            _transactionsStateUpdatedFlow.tryEmit(Unit)
        }
    }

    private fun activateReconciliationSession() {
        spentStatusReconciler.activate()
        keyImageSyncSession = null
        nativeSyncState = AdapterState.Syncing()
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private fun deactivateReconciliationSession() {
        spentStatusReconciler.deactivate()
        keyImageSyncSession = null
        explicitColdRecoveryPending = false
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private fun launchReconciliation(
        session: Long,
        expectedPhase: MoneroReconciliationPhase,
        expectedWallet: Wallet? = null,
        block: suspend () -> Unit,
    ) {
        spentStatusReconciler.launch(session, expectedPhase) {
            lifecycleMutex.withLock {
                val valid = spentStatusReconciler.isInPhase(session, expectedPhase) &&
                    isStarted &&
                    (expectedWallet == null || moneroWalletService.wallet === expectedWallet)
                if (!valid) return@withLock
                block()
            }
        }
    }

    override fun onWalletOpen(device: Wallet.Device?) {
        Timber.d("onWalletOpen: $device")
    }
}

/** Pure gates retained here so the fail-closed lifecycle contract is locally JVM-testable. */
internal fun canExposeMoneroSpendReady(
    hardwareWallet: Boolean,
    health: MoneroWalletHealthSnapshot,
    hasUnknownKeyImages: Boolean,
    durableState: MoneroSpentReconciliationState,
): Boolean =
    !hardwareWallet || (
        health.isFullyHealthy &&
            !hasUnknownKeyImages &&
            durableState == MoneroSpentReconciliationState.Ready
        )

/** JVM-only portion of the hardware-send boundary, checked before accessing the native wallet. */
internal fun isHardwareSpendLifecycleReady(
    syncState: AdapterState,
    durableState: MoneroSpentReconciliationState,
    spendReadiness: MoneroSpendReadiness,
): Boolean =
    syncState is AdapterState.Synced &&
        durableState == MoneroSpentReconciliationState.Ready &&
        spendReadiness == MoneroSpendReadiness.Ready

/**
 * Native start may synchronously deliver onWalletStarted() before [isStarted] is committed.
 * A callback is therefore accepted only for the active session while that start is in progress,
 * or once the start has completed.  With neither state true, stopped-session callbacks fail
 * closed.
 */
internal fun canAcceptWalletStartedCallback(
    hasActiveCurrentSession: Boolean,
    isStarted: Boolean,
    startInProgress: Boolean,
): Boolean = hasActiveCurrentSession && (isStarted || startInProgress)

internal fun walletStartedSyncState(health: MoneroWalletHealthSnapshot): AdapterState =
    when {
        hasMoneroNativeHealthFailure(health) ->
            AdapterState.NotSynced(IllegalStateException("Monero wallet native health check failed"))
        health.isFullyHealthy -> AdapterState.Synced
        else -> AdapterState.Syncing()
    }

internal fun hasMoneroNativeHealthFailure(health: MoneroWalletHealthSnapshot): Boolean =
    !health.callbackWalletIsCurrent ||
        !health.nativeStatusIsOk ||
        !health.nativeConnectionIsConnected

private fun nativeHealthFailureError(health: MoneroWalletHealthSnapshot): IllegalStateException =
    IllegalStateException(
        health.nativeStatusError?.takeIf { it.isNotBlank() }
            ?: "Monero wallet native health check failed",
    )

internal fun canFinalizeSpentReconciliation(health: MoneroWalletHealthSnapshot): Boolean =
    health.isFullyHealthy

internal enum class TrustedKeyImageSyncFinalizationOutcome {
    Ready,
    NeedsKeyImageSync,
    ReconciliationFailed,
}

internal fun trustedKeyImageSyncFinalizationOutcome(
    health: MoneroWalletHealthSnapshot,
    hasUnknownKeyImages: Boolean,
    syncState: AdapterState,
): TrustedKeyImageSyncFinalizationOutcome =
    when {
        hasUnknownKeyImages -> TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync
        health.isFullyHealthy && syncState is AdapterState.Synced ->
            TrustedKeyImageSyncFinalizationOutcome.Ready
        else -> TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed
    }

/**
 * A true return asks MoneroWalletService to suppress a same-tip callback. Hardware
 * reconciliation must therefore keep returning false until it reaches a terminal state: either
 * durably ready, or blocked on the user supplying key images.
 */
internal fun isRefreshCallbackFullyHandled(
    hardwareAccount: Boolean,
    hasActiveReconciliationOperation: Boolean,
    spendReadiness: MoneroSpendReadiness,
): Boolean =
    !hardwareAccount || (
        !hasActiveReconciliationOperation &&
            (spendReadiness == MoneroSpendReadiness.Ready ||
                spendReadiness == MoneroSpendReadiness.NeedsKeyImageSync ||
                spendReadiness == MoneroSpendReadiness.ReconciliationFailed)
        )

private val MoneroSpendReadiness.isTerminalKeyImageSyncOutcome: Boolean
    get() = this == MoneroSpendReadiness.Ready ||
        this == MoneroSpendReadiness.NeedsKeyImageSync ||
        this == MoneroSpendReadiness.ReconciliationFailed

internal enum class ColdKeyImageSyncNextStep {
    TrustedReady,
    PreserveKeyImagesRescan,
    NeedsKeyImageSync,
}

internal fun coldKeyImageSyncNextStep(
    spentStatusVerified: Boolean,
    hasUnknownKeyImages: Boolean,
): ColdKeyImageSyncNextStep =
    if (hasUnknownKeyImages) ColdKeyImageSyncNextStep.NeedsKeyImageSync
    else if (spentStatusVerified) ColdKeyImageSyncNextStep.TrustedReady
    else ColdKeyImageSyncNextStep.PreserveKeyImagesRescan

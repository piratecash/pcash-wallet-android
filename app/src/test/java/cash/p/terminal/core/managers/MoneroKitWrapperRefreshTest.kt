package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.service.MoneroWalletService
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitWrapperRefreshTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val account = Account(
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
    private val unsupportedAccount = account.copy(
        type = AccountType.EvmAddress("0x1234")
    )

    @Test
    fun refresh_syncingWallet_doesNotStopService() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)

        wrapper.refresh()

        verify(exactly = 0) { service.stop(any()) }
    }

    @Test
    fun refresh_pausedWalletFailedResume_restartsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, unsupportedAccount)
        every { service.resume(wrapper) } returns false

        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        wrapper.pause()
        wrapper.refresh()

        verify(exactly = 1) { service.resume(wrapper) }
        verify(exactly = 1) { service.stop(false) }
    }

    // recordNativeConnectionError is exercised directly (not through onRefreshed/statusInfo) because
    // those paths touch the native Wallet class (moneroWalletService.wallet), whose static
    // initializer loads libmonerujo and cannot run on the JVM. The helper has no such dependency.

    @Test
    fun recordNativeConnectionError_disconnectedRepeated_recordsOnce() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")

        verify(exactly = 1) { tracker.record(BlockchainType.Monero, account.id, any()) }
    }

    @Test
    fun recordNativeConnectionError_wrongVersion_recordsWithStatusName() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_WrongVersion, null)

        verify(exactly = 1) {
            tracker.record(
                BlockchainType.Monero,
                account.id,
                match { it.method == "ConnectionStatus_WrongVersion" }
            )
        }
    }

    @Test
    fun recordNativeConnectionError_connected_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Connected, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_nullStatus_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, null, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_trackerThrows_doesNotPropagate() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        every { tracker.record(any(), any(), any()) } throws RuntimeException("boom")
        val wrapper = createWrapper(mockService(), tracker = tracker)

        // Must not throw: the helper wraps recording in tryOrNull.
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, null)
    }

    @Test
    fun appendNetworkErrors_afterRecord_mergesIntoStatus() {
        // Contract that MoneroKitWrapper.statusInfo() relies on: a recorded error surfaces as
        // "Recent Network Error ..." keys in the merged status map.
        val tracker = NetworkErrorTracker()
        tracker.record(
            BlockchainType.Monero,
            account.id,
            NetworkErrorInfo(
                source = "Monero",
                method = "ConnectionStatus_Disconnected",
                url = "",
                host = "",
                resolvedIps = emptyList(),
                throwable = IllegalStateException("Not connected"),
            )
        )

        val merged = tracker.appendNetworkErrors(mapOf("isStarted" to true), BlockchainType.Monero, account.id)

        assertTrue(merged.keys.any { it.startsWith("Recent Network Error") })
    }

    private fun mockService(): MoneroWalletService {
        return mockk(relaxed = true)
    }

    private fun createWrapper(
        service: MoneroWalletService,
        account: Account = this.account,
        tracker: NetworkErrorTracker = mockk(relaxed = true),
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = tracker,
        )
    }

    private fun invokeRecord(
        wrapper: MoneroKitWrapper,
        status: ConnectionStatus?,
        errorString: String?
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "recordNativeConnectionError", ConnectionStatus::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(wrapper, status, errorString)
    }

    private fun setStarted(wrapper: MoneroKitWrapper) {
        privateField(wrapper, "isStarted").set(wrapper, true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSyncState(wrapper: MoneroKitWrapper, state: AdapterState) {
        val syncState = privateField(wrapper, "_syncState")
            .get(wrapper) as MutableStateFlow<AdapterState>
        syncState.value = state
    }

    private fun privateField(wrapper: MoneroKitWrapper, name: String) =
        wrapper.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }
}

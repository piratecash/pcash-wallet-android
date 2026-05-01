package cash.p.terminal.core.adapters.stellar

import cash.p.terminal.core.managers.StellarKitWrapper
import cash.p.terminal.wallet.AdapterState
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.stellarkit.SyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards: Stellar Send must unlock as soon as account balance is synced,
// independently of operations history.
class StellarAdapterSyncStateTest {

    private val stellarKit: StellarKit = mockk(relaxed = true)
    private val wrapper: StellarKitWrapper = mockk(relaxed = true)

    private fun createAdapter(
        balanceSync: SyncState = SyncState.Synced,
        operationsSync: SyncState = SyncState.Synced,
    ): StellarAdapter {
        every { wrapper.stellarKit } returns stellarKit
        every { stellarKit.syncStateFlow } returns MutableStateFlow(balanceSync)
        every { stellarKit.operationsSyncStateFlow } returns MutableStateFlow(operationsSync)
        return StellarAdapter(wrapper)
    }

    // --- balanceState: balance sync only ---

    @Test
    fun balanceState_balanceSyncedWhileOperationsSyncing_returnsSynced() {
        val adapter = createAdapter(operationsSync = SyncState.Syncing)

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_balanceSyncing_returnsSyncing() {
        val adapter = createAdapter(balanceSync = SyncState.Syncing)

        assertTrue(adapter.balanceState is AdapterState.Syncing)
    }

    @Test
    fun balanceState_balanceNotSynced_returnsNotSynced() {
        val err = Exception("horizon unavailable")
        val adapter = createAdapter(balanceSync = SyncState.NotSynced(err))

        val state = adapter.balanceState
        assertTrue(state is AdapterState.NotSynced)
        assertEquals(err, (state as AdapterState.NotSynced).error)
    }

    @Test
    fun balanceState_operationsNotSynced_returnsSynced_balanceDoesNotDependOnOperations() {
        val adapter = createAdapter(
            operationsSync = SyncState.NotSynced(Exception("operations endpoint down"))
        )

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    // --- transactionsSyncState: operations only ---

    @Test
    fun transactionsSyncState_operationsSyncing_returnsSearchingTxs() {
        val adapter = createAdapter(operationsSync = SyncState.Syncing)

        assertTrue(adapter.transactionsSyncState is AdapterState.SearchingTxs)
    }

    @Test
    fun transactionsSyncState_allSynced_returnsSynced() {
        val adapter = createAdapter()

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }

    @Test
    fun transactionsSyncState_operationsNotSynced_returnsNotSynced() {
        val err = Exception("operations endpoint down")
        val adapter = createAdapter(operationsSync = SyncState.NotSynced(err))

        val state = adapter.transactionsSyncState
        assertTrue(state is AdapterState.NotSynced)
        assertEquals(err, (state as AdapterState.NotSynced).error)
    }
}

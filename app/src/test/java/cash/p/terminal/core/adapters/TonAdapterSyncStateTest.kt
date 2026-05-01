package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.wallet.AdapterState
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.SyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards: TON Send must unlock as soon as account balance is synced,
// independently of event/jetton history.
class TonAdapterSyncStateTest {

    private val tonKit: TonKit = mockk(relaxed = true)
    private val wrapper: TonKitWrapper = mockk(relaxed = true)

    private fun createAdapter(
        accountSync: SyncState = SyncState.Synced,
        eventSync: SyncState = SyncState.Synced,
        jettonSync: SyncState = SyncState.Synced,
    ): TonAdapter {
        every { wrapper.tonKit } returns tonKit
        every { tonKit.syncStateFlow } returns MutableStateFlow(accountSync)
        every { tonKit.eventSyncStateFlow } returns MutableStateFlow(eventSync)
        every { tonKit.jettonSyncStateFlow } returns MutableStateFlow(jettonSync)
        every { tonKit.account } returns null
        return TonAdapter(wrapper)
    }

    private fun assertTransactionsNotSynced(adapter: TonAdapter, error: Throwable) {
        val state = adapter.transactionsSyncState
        assertTrue(state is AdapterState.NotSynced)
        assertEquals(error, (state as AdapterState.NotSynced).error)
    }

    // --- balanceState: account sync only ---

    @Test
    fun balanceState_accountSyncedWhileEventSyncing_returnsSynced() {
        val adapter = createAdapter(eventSync = SyncState.Syncing)

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_accountSyncedWhileJettonSyncing_returnsSynced() {
        val adapter = createAdapter(jettonSync = SyncState.Syncing)

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_accountSyncing_returnsSyncing() {
        val adapter = createAdapter(accountSync = SyncState.Syncing)

        assertTrue(adapter.balanceState is AdapterState.Syncing)
    }

    @Test
    fun balanceState_accountNotSynced_returnsNotSynced() {
        val err = Exception("ton api unavailable")
        val adapter = createAdapter(accountSync = SyncState.NotSynced(err))

        val state = adapter.balanceState
        assertTrue(state is AdapterState.NotSynced)
        assertEquals(err, (state as AdapterState.NotSynced).error)
    }

    // --- transactionsSyncState: events + jettons overlay ---

    @Test
    fun transactionsSyncState_eventSyncing_returnsSearchingTxs() {
        val adapter = createAdapter(eventSync = SyncState.Syncing)

        assertTrue(adapter.transactionsSyncState is AdapterState.SearchingTxs)
    }

    @Test
    fun transactionsSyncState_jettonSyncing_returnsSearchingTxs() {
        val adapter = createAdapter(jettonSync = SyncState.Syncing)

        assertTrue(adapter.transactionsSyncState is AdapterState.SearchingTxs)
    }

    @Test
    fun transactionsSyncState_allSynced_returnsSynced() {
        val adapter = createAdapter()

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }

    @Test
    fun transactionsSyncState_eventNotSynced_returnsNotSynced() {
        val err = Exception("event source down")
        val adapter = createAdapter(eventSync = SyncState.NotSynced(err))

        assertTransactionsNotSynced(adapter, err)
    }

    @Test
    fun transactionsSyncState_jettonNotSynced_returnsNotSynced() {
        val err = Exception("jetton source down")
        val adapter = createAdapter(jettonSync = SyncState.NotSynced(err))

        assertTransactionsNotSynced(adapter, err)
    }

    @Test
    fun transactionsSyncState_eventNotSyncedWhileJettonSyncing_returnsNotSynced() {
        val err = Exception("event source down")
        val adapter = createAdapter(
            eventSync = SyncState.NotSynced(err),
            jettonSync = SyncState.Syncing,
        )

        assertTransactionsNotSynced(adapter, err)
    }

    @Test
    fun transactionsSyncState_jettonNotSyncedWhileEventSyncing_returnsNotSynced() {
        val err = Exception("jetton source down")
        val adapter = createAdapter(
            eventSync = SyncState.Syncing,
            jettonSync = SyncState.NotSynced(err),
        )

        assertTransactionsNotSynced(adapter, err)
    }
}

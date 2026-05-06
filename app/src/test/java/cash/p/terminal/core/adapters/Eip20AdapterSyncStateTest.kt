package cash.p.terminal.core.adapters

import android.content.Context
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit.ForwardSyncState
import io.horizontalsystems.ethereumkit.core.EthereumKit.HistoricalSyncState
import io.horizontalsystems.ethereumkit.core.EthereumKit.SyncError
import io.horizontalsystems.ethereumkit.core.EthereumKit.SyncState
import io.horizontalsystems.ethereumkit.models.Address
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Guards: BEP-20 Send/Swap must unlock as soon as eth_getBalance returns,
// independently of ERC-20 history sync.
class Eip20AdapterSyncStateTest {

    private val context: Context = mockk(relaxed = true)
    private val repository: EvmTransactionRepository = mockk(relaxed = true)
    private val coinManager: ICoinManager = mockk(relaxed = true)
    private val baseToken: Token = mockk(relaxed = true)
    private val wallet: Wallet = mockk(relaxed = true)
    private val labelManager: EvmLabelManager = mockk(relaxed = true)
    private val stackingManager: StackingManager = mockk(relaxed = true)
    private val erc20Kit: Erc20Kit = mockk(relaxed = true)

    private fun createAdapter(
        eip20SyncState: SyncState = SyncState.Synced(),
        historicalSyncState: HistoricalSyncState = HistoricalSyncState.Idle,
        forwardSyncState: ForwardSyncState = ForwardSyncState.Idle,
        evmTxSyncState: SyncState = SyncState.Synced(),
        blockchainType: BlockchainType = BlockchainType.BinanceSmartChain,
    ): Eip20Adapter {
        every { wallet.decimal } returns 18
        every { erc20Kit.syncState } returns eip20SyncState
        every { repository.buildErc20Kit(any(), any<Address>()) } returns erc20Kit
        every { repository.historicalSyncState } returns MutableStateFlow(historicalSyncState)
        every { repository.forwardSyncState } returns MutableStateFlow(forwardSyncState)
        every { repository.transactionsSyncState } returns evmTxSyncState
        every { repository.getBlockchainType() } returns blockchainType
        every { stackingManager.unpaidFlow } returns MutableStateFlow(BigDecimal.ZERO)

        return Eip20Adapter(
            context = context,
            evmTransactionRepository = repository,
            contractAddress = "0x0000000000000000000000000000000000000001",
            baseToken = baseToken,
            coinManager = coinManager,
            wallet = wallet,
            evmLabelManager = labelManager,
            stackingManager = stackingManager,
        )
    }

    // --- balanceState: pure mapping of eip20Kit.syncState ---

    @Test
    fun balanceState_eip20Synced_returnsSynced() {
        val adapter = createAdapter(eip20SyncState = SyncState.Synced())

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_eip20SyncedWhileHistoricalScanning_returnsSyncedNotOverlayed() {
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            historicalSyncState = HistoricalSyncState.Syncing(
                startBlock = 83_000_000,
                currentBlock = 50_000_000,
            ),
        )

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_eip20SyncedWhileForwardSyncing_returnsSynced() {
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            forwardSyncState = ForwardSyncState.Syncing(
                lastSyncedTip = 83_000_000,
                chainTipBlock = 83_000_500,
            ),
        )

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun balanceState_eip20Syncing_returnsSyncing() {
        val adapter = createAdapter(eip20SyncState = SyncState.Syncing())

        assertTrue(adapter.balanceState is AdapterState.Syncing)
    }

    @Test
    fun balanceState_eip20NotSynced_returnsNotSynced() {
        val err = Exception("rpc unavailable")
        val adapter = createAdapter(eip20SyncState = SyncState.NotSynced(err))

        val state = adapter.balanceState
        assertTrue(state is AdapterState.NotSynced)
        assertEquals(err, (state as AdapterState.NotSynced).error)
    }

    // --- transactionsSyncState: overlay is exposed here ---

    @Test
    fun transactionsSyncState_historicalSyncing_returnsSyncedNotOverlayed() {
        // Historical sync is intentionally NOT surfaced as a "Syncing" state, otherwise
        // the UI would render a misleading "~89.7M blocks remaining" message on BSC.
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            historicalSyncState = HistoricalSyncState.Syncing(
                startBlock = 83_000_000,
                currentBlock = 50_000_000,
            ),
        )

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }

    @Test
    fun transactionsSyncState_forwardSyncing_returnsSyncing() {
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            forwardSyncState = ForwardSyncState.Syncing(
                lastSyncedTip = 83_000_000,
                chainTipBlock = 83_000_500,
            ),
        )

        val txState = adapter.transactionsSyncState
        assertTrue(txState is AdapterState.Syncing)
        assertEquals(500L, (txState as AdapterState.Syncing).blocksRemained)
    }

    @Test
    fun transactionsSyncState_allIdleAndEvmTxSynced_returnsSynced() {
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            evmTxSyncState = SyncState.Synced(),
        )

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }

    @Test
    fun transactionsSyncState_evmTxNotStarted_returnsSynced() {
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            evmTxSyncState = SyncState.NotSynced(SyncError.NotStarted()),
        )

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }

    @Test
    fun transactionsSyncState_evmTxSyncError_returnsNotSynced() {
        val err = Exception("etherscan unavailable")
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            evmTxSyncState = SyncState.NotSynced(err),
        )

        val txState = adapter.transactionsSyncState
        assertTrue(txState is AdapterState.NotSynced)
        assertEquals(err, (txState as AdapterState.NotSynced).error)
    }

    @Test
    fun transactionsSyncState_nonBscWithHistoricalSyncing_returnsSynced() {
        // Sanity check: the same "no historical overlay" rule holds on non-BSC chains too.
        val adapter = createAdapter(
            eip20SyncState = SyncState.Synced(),
            historicalSyncState = HistoricalSyncState.Syncing(
                startBlock = 100_000,
                currentBlock = 50_000,
            ),
            blockchainType = BlockchainType.Ethereum,
        )

        assertEquals(AdapterState.Synced, adapter.transactionsSyncState)
    }
}

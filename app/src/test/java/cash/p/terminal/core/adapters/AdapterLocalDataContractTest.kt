package cash.p.terminal.core.adapters

import android.content.Context
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.stellar.StellarAdapter
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapterTestFixture
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.MoneroKitWrapper
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.core.managers.StellarKitWrapper
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tronkit.TronKit
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * `attachLocalData()` must attach local collectors only, never a network call. Kit mocks are
 * cleared after construction, which may legitimately touch the kit, so each test observes
 * `attachLocalData()` alone.
 */
class AdapterLocalDataContractTest : ZcashAdapterTestFixture() {

    @Before
    fun setUpAdditionalKoinModules() {
        loadKoinModules(
            module {
                single { mockk<ICoinManager>(relaxed = true) }
                single { mockk<EvmLabelManager>(relaxed = true) }
                single { mockk<NetworkErrorTracker>(relaxed = true) }
            }
        )
    }

    @Test
    fun attachLocalData_evmAdapter_doesNotTouchNetwork() {
        val repository = mockk<EvmTransactionRepository>(relaxed = true)
        val adapter = EvmAdapter(repository, mockk<ICoinManager>(relaxed = true))
        clearMocks(repository, answers = false)

        adapter.attachLocalData()

        verify { repository wasNot Called }
    }

    @Test
    fun attachLocalData_eip20Adapter_doesNotTouchNetwork() {
        val repository = mockk<EvmTransactionRepository>(relaxed = true)
        val eip20Kit = mockk<Erc20Kit>(relaxed = true)
        every { repository.buildErc20Kit(any(), any()) } returns eip20Kit
        val wallet = WalletFactory.previewStakingWallet()
        val adapter = Eip20Adapter(
            context = mockk<Context>(relaxed = true),
            evmTransactionRepository = repository,
            contractAddress = "0x0000000000000000000000000000000000000001",
            baseToken = wallet.token,
            coinManager = mockk<ICoinManager>(relaxed = true),
            wallet = wallet,
            evmLabelManager = mockk<EvmLabelManager>(relaxed = true),
            stackingManager = mockk<StackingManager>(relaxed = true),
        )
        clearMocks(eip20Kit, answers = false)

        adapter.attachLocalData()

        verify { eip20Kit wasNot Called }
    }

    @Test
    fun attachLocalData_tonAdapter_doesNotTouchNetwork() {
        val tonKit = mockk<TonKit>(relaxed = true)
        val adapter = TonAdapter(TonKitWrapper(tonKit, mockk<TonWallet>(relaxed = true)))
        clearMocks(tonKit, answers = false)

        adapter.attachLocalData()

        verify { tonKit wasNot Called }
    }

    @Test
    fun attachLocalData_jettonAdapter_attachesOnlyLocalCollectors() {
        val tonKit = mockk<TonKit>(relaxed = true)
        every { tonKit.jettonSyncStateFlow } returns
            MutableStateFlow(io.horizontalsystems.tonkit.models.SyncState.Synced)
        every { tonKit.jettonBalanceMapFlow } returns MutableStateFlow(emptyMap())
        val coinManager = mockk<ICoinManager>(relaxed = true)
        every { coinManager.getToken(any()) } returns mockk<Token>(relaxed = true)
        val adapter = JettonAdapter(
            coinManager = coinManager,
            tonKitWrapper = TonKitWrapper(tonKit, mockk<TonWallet>(relaxed = true)),
            addressStr = TON_ADDRESS,
            wallet = WalletFactory.previewWallet(),
        )

        try {
            adapter.attachLocalData()

            // Local collectors on jettonBalanceMapFlow/jettonSyncStateFlow are expected; a real
            // network refresh is not.
            coVerify(exactly = 0) { tonKit.refresh() }
        } finally {
            // attachLocalData() launches real collectors on Dispatchers.Default; stop() cancels
            // them so they don't leak into later tests in the same JVM.
            adapter.stop()
        }
    }

    @Test
    fun attachLocalData_tronAdapter_doesNotTouchNetwork() {
        val tronKit = mockk<TronKit>(relaxed = true)
        val adapter = TronAdapter(TronKitWrapper(tronKit, signer = null))
        clearMocks(tronKit, answers = false)

        adapter.attachLocalData()

        verify { tronKit wasNot Called }
    }

    @Test
    fun attachLocalData_trc20Adapter_doesNotTouchNetwork() {
        val tronKit = mockk<TronKit>(relaxed = true)
        val wallet = WalletFactory.previewWallet()
        val adapter = Trc20Adapter(
            tronKitWrapper = TronKitWrapper(tronKit, signer = null),
            contractAddress = TRON_ADDRESS,
            wallet = wallet,
            baseToken = wallet.token,
        )
        clearMocks(tronKit, answers = false)

        adapter.attachLocalData()

        verify { tronKit wasNot Called }
    }

    @Test
    fun attachLocalData_solanaAdapter_doesNotTouchNetwork() {
        val solanaKit = mockk<SolanaKit>(relaxed = true)
        val adapter = SolanaAdapter(SolanaKitWrapper(solanaKit, signer = null))
        clearMocks(solanaKit, answers = false)

        adapter.attachLocalData()

        verify { solanaKit wasNot Called }
    }

    @Test
    fun attachLocalData_splAdapter_doesNotTouchNetwork() {
        val solanaKit = mockk<SolanaKit>(relaxed = true)
        val wallet = WalletFactory.previewWallet()
        val adapter = SplAdapter(SolanaKitWrapper(solanaKit, signer = null), wallet, SOLANA_ADDRESS)
        clearMocks(solanaKit, answers = false)

        adapter.attachLocalData()

        verify { solanaKit wasNot Called }
    }

    @Test
    fun attachLocalData_stellarAdapter_attachesOnlyLocalCollectors() {
        val stellarKit = mockk<StellarKit>(relaxed = true)
        val adapter = StellarAdapter(StellarKitWrapper(stellarKit))

        try {
            adapter.attachLocalData()

            coVerify(exactly = 0) { stellarKit.refresh() }
        } finally {
            // attachLocalData() launches real collectors on Dispatchers.Default; stop() cancels
            // them so they don't leak into later tests in the same JVM.
            adapter.stop()
        }
    }

    @Test
    fun attachLocalData_stellarAssetAdapter_attachesOnlyLocalCollectors() {
        val stellarKit = mockk<StellarKit>(relaxed = true)
        every { stellarKit.syncStateFlow } returns
            MutableStateFlow(io.horizontalsystems.stellarkit.SyncState.Synced)
        val adapter = StellarAssetAdapter(StellarKitWrapper(stellarKit), code = "USDC", issuer = "issuer")

        try {
            adapter.attachLocalData()

            coVerify(exactly = 0) { stellarKit.refresh() }
        } finally {
            // attachLocalData() launches real collectors on Dispatchers.Default; stop() cancels
            // them so they don't leak into later tests in the same JVM.
            adapter.stop()
        }
    }

    @Test
    fun attachLocalData_bitcoinAdapter_doesNotTouchNetwork() {
        val kit = mockk<BitcoinKit>(relaxed = true)
        val backgroundManager = mockk<BackgroundManager>(relaxed = true)
        every { backgroundManager.stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
        val adapter = BitcoinAdapter(
            kit = kit,
            syncMode = BitcoinCore.SyncMode.Full(),
            backgroundManager = backgroundManager,
            wallet = WalletFactory.previewWallet(),
        )
        clearMocks(kit, answers = false)

        try {
            adapter.attachLocalData()

            verify(exactly = 0) { kit.start() }
        } finally {
            // attachLocalData() launches a real collector on Dispatchers.Default; stop() cancels
            // it so it doesn't leak into later tests in the same JVM.
            adapter.stop()
        }
    }

    @Test
    fun attachLocalData_moneroAdapter_doesNotTouchNetwork() {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)
        clearMocks(moneroKitWrapper, answers = false)

        adapter.attachLocalData()

        verify { moneroKitWrapper wasNot Called }
    }

    @Test
    fun attachLocalData_zcashAdapter_doesNotTouchNetwork() {
        // The synchronizer is itself the local-data source (balances/transactions/progress are
        // Room-backed flows), so the contract here is "no network entry point", not "no call".
        adapter = createAdapter()
        clearMocks(mockSynchronizer, answers = false)

        adapter.attachLocalData()

        coVerify(exactly = 0) { mockSynchronizer.resumeSync() }
        coVerify(exactly = 0) { mockSynchronizer.refreshAllBalances() }
        verify(exactly = 0) { mockSynchronizer.refreshTransactions() }
    }

    private companion object {
        const val TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val SOLANA_ADDRESS = "11111111111111111111111111111111"
        const val TON_ADDRESS = "UQCYTBH7n8OnQ6BgOfdkNRWF7socLJb9U-JMRcoz3UpL_0V6"
    }
}

package cash.p.terminal.modules.send.offline

import cash.p.terminal.R
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.EvmError
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineStellarRetryMetadata
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.entities.OfflineTokenMetadata
import cash.p.terminal.entities.OfflineTronRetryMetadata
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineBroadcastViewModelTest {

    private interface TestOfflineTransactionAdapter : IAdapter, OfflineTransactionAdapter<Any>

    private val dispatcher = UnconfinedTestDispatcher()

    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>(relaxed = true)
    private val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val tokenResolver = mockk<OfflineBroadcastTokenResolver>(relaxed = true)
    private val dispatcherProvider = mockk<DispatcherProvider>(relaxed = true)

    private val bitcoin = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)
    private val bitcoinToken = token(bitcoin)
    private val account = mnemonicAccount()
    private val bitcoinWallet = wallet(bitcoinToken, account)
    private val binanceSmartChain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
    private val bnbToken = token(
        blockchain = binanceSmartChain,
        coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
        tokenType = TokenType.Native,
        decimals = 18,
    )
    private val usdtToken = token(
        blockchain = binanceSmartChain,
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        tokenType = TokenType.Eip20("0x55d398326f99059ff775485246999027b3197955"),
        decimals = 18,
    )
    private val bnbWallet = wallet(bnbToken, account)
    private val usdtWallet = wallet(usdtToken, account)
    private val solana = Blockchain(BlockchainType.Solana, "Solana", null)
    private val solanaToken = token(
        blockchain = solana,
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        decimals = 9,
    )
    private val solanaWallet = wallet(solanaToken, account)
    private val ton = Blockchain(BlockchainType.Ton, "TON", null)
    private val tonToken = token(
        blockchain = ton,
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        decimals = 9,
    )
    private val tonWallet = wallet(tonToken, account)
    private val tron = Blockchain(BlockchainType.Tron, "TRON", null)
    private val tronToken = token(
        blockchain = tron,
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        decimals = 6,
    )
    private val tronWallet = wallet(tronToken, account)
    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val stellarToken = token(
        blockchain = stellar,
        coin = Coin(uid = "stellar", name = "Stellar", code = "XLM"),
        decimals = 7,
    )
    private val stellarWallet = wallet(stellarToken, account)
    private val monero = Blockchain(BlockchainType.Monero, "Monero", null)
    private val moneroToken = token(
        blockchain = monero,
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        decimals = 12,
    )
    private val moneroWallet = wallet(moneroToken, account)
    private val zcash = Blockchain(BlockchainType.Zcash, "Zcash", null)
    private val zcashToken = token(
        blockchain = zcash,
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        tokenType = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        decimals = 8,
    )
    private val zcashWallet = wallet(zcashToken, account)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { dispatcherProvider.io } returns dispatcher
        every { dispatcherProvider.default } returns dispatcher
        every { dispatcherProvider.main } returns dispatcher
        setActiveWallets(emptyList())
        every { accountManager.activeAccount } returns account
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(bitcoinToken)
    }

    private fun setActiveWallets(wallets: List<Wallet>) {
        every { walletManager.activeWallets } returns wallets
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun prefillAndAdvance_pcashPayloadWithExistingWallet_showsReadyToSend() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertEquals(OfflineBroadcastStep.Confirm, viewModel.uiState.step)
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.Send)
        assertNull(confirm?.enableNetworkName)
        coVerify { repository.saveImported(bitcoinWallet, any(), any()) }
    }

    @Test
    fun prefillAndAdvance_pcashPayloadWithTokenWalletBeforeNative_savesNativeWallet() =
        runTest(dispatcher) {
            setActiveWallets(listOf(usdtWallet, bnbWallet))
            every { payloadEncoder.decode(any()) } returns decoded(blockchainUid = "binance-smart-chain")
            every { marketKit.blockchain("binance-smart-chain") } returns binanceSmartChain

            val viewModel = createViewModel()
            viewModel.prefillAndAdvance("pcash:tx:v1:payload")
            advanceUntilIdle()

            coVerify { repository.saveImported(bnbWallet, any(), any()) }
            coVerify(exactly = 0) { repository.saveImported(usdtWallet, any(), any()) }
        }

    @Test
    fun prefillAndAdvance_pcashPayloadMissingWallet_offersEnableNetwork() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertEquals(OfflineBroadcastStep.Confirm, viewModel.uiState.step)
        assertEquals("Bitcoin", confirm?.enableNetworkName)
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.EnableNetwork)
        // Nothing is persisted until the wallet actually exists.
        coVerify(exactly = 0) { repository.saveImported(any(), any(), any()) }
    }

    @Test
    fun onEnableNetwork_walletAndAdapterReady_armsSendAndPersists() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken
        coEvery { walletUseCase.createWallets(any()) } answers {
            setActiveWallets(listOf(bitcoinWallet))
            true
        }
        coEvery {
            adapterManager.awaitAdapterForWallet<IAdapter>(any(), any())
        } returns mockk<TestOfflineTransactionAdapter>()

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.Send)
        assertNull(viewModel.uiState.errorMessage)
        coVerify { walletUseCase.createWallets(setOf(bitcoinToken)) }
        coVerify { repository.saveImported(bitcoinWallet, any(), any()) }
    }

    @Test
    fun onEnableNetwork_createWalletsFails_keepsEnableActionAndShowsError() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken
        coEvery { walletUseCase.createWallets(any()) } returns false

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.EnableNetwork)
        assertNotNull(viewModel.uiState.errorMessage)
        coVerify(exactly = 0) { repository.saveImported(any(), any(), any()) }
        // Non-hardware wallets persist synchronously, so a failed enable must fail fast and never block
        // on the bounded wait that only makes sense for asynchronous hardware enables.
        coVerify(exactly = 0) { walletUseCase.awaitWallets(any()) }
    }

    @Test
    fun onEnableNetwork_adapterNeverReady_keepsEnableActionAndShowsError() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken
        coEvery { walletUseCase.createWallets(any()) } answers {
            setActiveWallets(listOf(bitcoinWallet))
            true
        }
        coEvery {
            adapterManager.awaitAdapterForWallet<IAdapter>(any(), any())
        } returns null

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.confirm?.action is OfflineBroadcastConfirmAction.EnableNetwork)
        assertNotNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun onEnableNetwork_hardwareWalletNeverAppears_keepsEnableActionAndShowsError() = runTest(dispatcher) {
        every { accountManager.activeAccount } returns hardwareAccount()
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken
        // Hardware creation reports success but the wallet never propagates, so the bounded wait must
        // time out instead of stranding the UI in "Preparing".
        coEvery { walletUseCase.createWallets(any()) } returns true
        coEvery { walletUseCase.awaitWallets(any()) } coAnswers { awaitCancellation() }

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.confirm?.action is OfflineBroadcastConfirmAction.EnableNetwork)
        assertNotNull(viewModel.uiState.errorMessage)
        // The hardware path must actually wait for the asynchronous wallet before giving up.
        coVerify { walletUseCase.awaitWallets(any()) }
    }

    @Test
    fun prefillAndAdvance_unsupportedAccountType_dismissesWithError() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { accountManager.activeAccount } returns mnemonicAccount()
        every { payloadEncoder.decode(any()) } returns decoded(blockchainUid = "ethereum")
        every { marketKit.blockchain("ethereum") } returns Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns null

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.dismissError)
        coVerify(exactly = 0) { walletUseCase.createWallets(any()) }
    }

    @Test
    fun prefillAndAdvance_watchOnlyAccount_rejectsWithoutEnabling() = runTest(dispatcher) {
        every { accountManager.activeAccount } returns watchAccount()
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        every {
            tokenResolver.resolveTokenToEnable(BlockchainType.Bitcoin, match { it.isWatchAccount })
        } returns null

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.dismissError)
        coVerify(exactly = 0) { walletUseCase.createWallets(any()) }
    }

    @Test
    fun onBroadcast_watchOnlyAccount_neverBroadcasts() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>(relaxed = true)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        // Account turns watch-only before the user taps send.
        every { accountManager.activeAccount } returns watchAccount()
        every {
            tokenResolver.resolveTokenToEnable(BlockchainType.Bitcoin, match { it.isWatchAccount })
        } returns null
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.dismissError)
        coVerify(exactly = 0) { adapter.broadcastRawTransaction(any(), any()) }
    }

    @Test
    fun onBroadcast_adapterReady_submitsAndShowsSuccess() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", null) }
    }

    @Test
    fun onBroadcast_solanaPcashPayload_passesRetryMetadata() = runTest(dispatcher) {
        val retryMetadata = OfflineSolanaRetryMetadata(
            blockHash = "block-hash",
            lastValidBlockHeight = 123L,
        )
        setActiveWallets(listOf(solanaWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "solana",
            txHash = SOLANA_SIGNATURE,
            solanaRetryMetadata = retryMetadata,
        )
        every { marketKit.blockchain("solana") } returns solana
        val adapter = mockk<TestOfflineTransactionAdapter>()
        val broadcastMetadata = OfflineBroadcastMetadata.Solana(
            blockHash = "block-hash",
            lastValidBlockHeight = 123L,
        )
        coEvery {
            adapter.broadcastRawTransaction(any(), broadcastMetadata)
        } returns BroadcastRawTransactionResult(SOLANA_SIGNATURE, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", broadcastMetadata) }
        coVerify { repository.markBroadcasted("account-id", SOLANA_SIGNATURE, SOLANA_SIGNATURE) }
    }

    @Test
    fun onBroadcast_tonPcashPayload_passesRetryMetadata() = runTest(dispatcher) {
        val retryMetadata = OfflineTonRetryMetadata(
            validUntil = 1_700_000_300L,
            senderAddress = "EQSender",
            seqno = 7,
        )
        setActiveWallets(listOf(tonWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "the-open-network",
            txHash = TON_MESSAGE_HASH,
            tonRetryMetadata = retryMetadata,
        )
        every { marketKit.blockchain("the-open-network") } returns ton
        val adapter = mockk<TestOfflineTransactionAdapter>()
        val broadcastMetadata = OfflineBroadcastMetadata.Ton(
            validUntil = 1_700_000_300L,
            senderAddress = "EQSender",
            seqno = 7,
        )
        coEvery {
            adapter.broadcastRawTransaction(any(), broadcastMetadata)
        } returns BroadcastRawTransactionResult(TON_MESSAGE_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", broadcastMetadata) }
        coVerify { repository.markBroadcasted("account-id", TON_MESSAGE_HASH, TON_MESSAGE_HASH) }
    }

    @Test
    fun onBroadcast_tronPcashPayload_passesRetryMetadata() = runTest(dispatcher) {
        val retryMetadata = OfflineTronRetryMetadata(expiration = 1_700_000_060_000L)
        setActiveWallets(listOf(tronWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "tron",
            txHash = TRON_TX_HASH,
            tronRetryMetadata = retryMetadata,
        )
        every { marketKit.blockchain("tron") } returns tron
        val adapter = mockk<TestOfflineTransactionAdapter>()
        val broadcastMetadata = OfflineBroadcastMetadata.Tron(expiration = 1_700_000_060_000L)
        coEvery {
            adapter.broadcastRawTransaction(any(), broadcastMetadata)
        } returns BroadcastRawTransactionResult(TRON_TX_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", broadcastMetadata) }
        coVerify { repository.markBroadcasted("account-id", TRON_TX_HASH, TRON_TX_HASH) }
    }

    @Test
    fun onBroadcast_stellarPcashPayload_passesRetryMetadata() = runTest(dispatcher) {
        val retryMetadata = OfflineStellarRetryMetadata(
            sourceAccountId = "GSource",
            sequenceNumber = 123_456_789L,
            validUntil = 1_700_000_180L,
        )
        setActiveWallets(listOf(stellarWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "stellar",
            txHash = STELLAR_TX_HASH,
            stellarRetryMetadata = retryMetadata,
        )
        every { marketKit.blockchain("stellar") } returns stellar
        val adapter = mockk<TestOfflineTransactionAdapter>()
        val broadcastMetadata = OfflineBroadcastMetadata.Stellar(
            sourceAccountId = "GSource",
            sequenceNumber = 123_456_789L,
            validUntil = 1_700_000_180L,
        )
        coEvery {
            adapter.broadcastRawTransaction(any(), broadcastMetadata)
        } returns BroadcastRawTransactionResult(STELLAR_TX_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", broadcastMetadata) }
        coVerify { repository.markBroadcasted("account-id", STELLAR_TX_HASH, STELLAR_TX_HASH) }
    }

    @Test
    fun onBroadcast_moneroPcashPayload_passesNullMetadata() = runTest(dispatcher) {
        setActiveWallets(listOf(moneroWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "monero",
            txHash = MONERO_TX_HASH,
        )
        every { marketKit.blockchain("monero") } returns monero
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery {
            adapter.broadcastRawTransaction(any(), null)
        } returns BroadcastRawTransactionResult(MONERO_TX_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", null) }
        coVerify { repository.markBroadcasted("account-id", MONERO_TX_HASH, MONERO_TX_HASH) }
    }

    @Test
    fun onBroadcast_zcashPcashPayload_passesTxHashMetadata() = runTest(dispatcher) {
        setActiveWallets(listOf(zcashWallet))
        every {
            payloadEncoder.decode(any())
        } returns decoded(
            blockchainUid = "zcash",
            txHash = ZCASH_TX_HASH,
        )
        every { marketKit.blockchain("zcash") } returns zcash
        val adapter = mockk<TestOfflineTransactionAdapter>()
        val broadcastMetadata = OfflineBroadcastMetadata.Zcash(txHash = ZCASH_TX_HASH)
        coEvery {
            adapter.broadcastRawTransaction(any(), broadcastMetadata)
        } returns BroadcastRawTransactionResult(ZCASH_TX_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", broadcastMetadata) }
        coVerify { repository.markBroadcasted("account-id", ZCASH_TX_HASH, ZCASH_TX_HASH) }
    }

    @Test
    fun onBroadcast_zcashPlainRawHex_passesNullMetadataAndShowsError() = runTest(dispatcher) {
        setActiveWallets(listOf(zcashWallet))
        every { payloadEncoder.decode(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(zcashToken)
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery {
            adapter.broadcastRawTransaction(any(), null)
        } throws UnsupportedOperationException()
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(zcash)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Error)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", null) }
        coVerify(exactly = 0) { repository.saveRawImported(any(), any(), any()) }
    }

    @Test
    fun onBroadcast_tonPlainRawHex_passesNullMetadata() = runTest(dispatcher) {
        setActiveWallets(listOf(tonWallet))
        every { payloadEncoder.decode(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(tonToken)
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult(TON_MESSAGE_HASH, BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(ton)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", null) }
    }

    @Test
    fun prefillAndAdvance_tonWatchAccount_allowsRelay() = runTest(dispatcher) {
        val watchAccount = watchAccount()
        every { accountManager.activeAccount } returns watchAccount
        setActiveWallets(listOf(wallet(tonToken, watchAccount)))
        every { payloadEncoder.decode(any()) } returns decoded(
            blockchainUid = "the-open-network",
            txHash = TON_MESSAGE_HASH,
            tonRetryMetadata = OfflineTonRetryMetadata(
                validUntil = 1_700_000_300L,
                senderAddress = "EQSender",
                seqno = 7,
            ),
        )
        every { marketKit.blockchain("the-open-network") } returns ton
        every { tokenResolver.resolveTokenToEnable(BlockchainType.Ton, watchAccount) } returns tonToken

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Confirm, viewModel.uiState.step)
        assertTrue(viewModel.uiState.confirm?.action is OfflineBroadcastConfirmAction.Send)
        assertNull(viewModel.uiState.dismissError)
    }

    @Test
    fun onBroadcast_adapterQueues_keepsRecordPendingAndShowsQueuedSuccess() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Queued)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val result = viewModel.uiState.result as? OfflineBroadcastResult.Success
        assertTrue(result?.queued == true)
        // A queued send did not reach the network, so the record must stay Pending ("awaiting send")
        // and only the attempt is recorded — never a false "Sent".
        coVerify { repository.markBroadcastAttempt("account-id", "hash") }
        coVerify(exactly = 0) { repository.markBroadcasted(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastedByRawHex(any(), any()) }
    }

    @Test
    fun onBroadcast_plainRawHexSubmitted_persistsBroadcastedRawTransaction() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("derived-hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerifyOrder {
            repository.saveRawImported(bitcoinWallet, "deadbeefdeadbeefdead", "derived-hash")
            repository.markBroadcastAttempt("account-id", "derived-hash")
            repository.markBroadcasted("account-id", "derived-hash", "derived-hash")
            repository.markBroadcastedByRawHex("deadbeefdeadbeefdead", "derived-hash")
        }
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead", null) }
    }

    @Test
    fun onBroadcast_plainRawHexQueued_persistsPendingRawTransaction() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("queued-hash", BroadcastRawTransactionStatus.Queued)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerifyOrder {
            repository.saveRawImported(bitcoinWallet, "deadbeefdeadbeefdead", "queued-hash")
            repository.markBroadcastAttempt("account-id", "queued-hash")
        }
        coVerify(exactly = 0) { repository.markBroadcasted(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastedByRawHex(any(), any()) }
    }

    @Test
    fun onBroadcast_importStillSaving_persistsImportBeforeMarkingBroadcast() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter
        // Keep the import insert in flight while the user reaches broadcast.
        val importGate = CompletableDeferred<Unit>()
        coEvery { repository.saveImported(any(), any(), any()) } coAnswers { importGate.await() }

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        // Broadcast must block on the still-pending insert: no status write may target a not-yet-saved row.
        coVerify(exactly = 0) { repository.markBroadcastAttempt(any(), any()) }

        importGate.complete(Unit)
        advanceUntilIdle()

        coVerifyOrder {
            repository.saveImported(bitcoinWallet, any(), any())
            repository.markBroadcastAttempt("account-id", "hash")
            repository.markBroadcasted("account-id", "hash", "hash")
            repository.markBroadcastedByRawHex("deadbeefdeadbeefdead", "hash")
        }
    }

    @Test
    fun onBroadcast_adapterDerivesDifferentTxHash_reconcilesRecordToDerivedHash() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        // The payload claimed "hash"; the kit derives the real txid from the raw bytes.
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("derived-hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerify { repository.markBroadcasted("account-id", "hash", "derived-hash") }
        coVerify { repository.markBroadcastedByRawHex("deadbeefdeadbeefdead", "derived-hash") }
    }

    @Test
    fun onBroadcast_adapterReturnsAlreadyKnown_showsAlreadyInNetworkWithoutMutatingRecord() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.AlreadyKnown)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val result = viewModel.uiState.result as? OfflineBroadcastResult.Error
        assertNotNull(result)
        assertEquals(Translator.getString(R.string.offline_broadcast_error_already_sent), result?.message)
        coVerify(exactly = 0) { repository.markBroadcastAttempt(any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcasted(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastedByRawHex(any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastFailed(any(), any(), any()) }
    }

    @Test
    fun onBroadcast_plainRawHexAlreadyKnown_showsAlreadyInNetworkWithoutPersistingRecord() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } returns
            BroadcastRawTransactionResult("derived-hash", BroadcastRawTransactionStatus.AlreadyKnown)
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val result = viewModel.uiState.result as? OfflineBroadcastResult.Error
        assertNotNull(result)
        assertEquals(Translator.getString(R.string.offline_broadcast_error_already_sent), result?.message)
        coVerify(exactly = 0) { repository.saveRawImported(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastAttempt(any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcasted(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markBroadcastedByRawHex(any(), any()) }
    }

    @Test
    fun onBroadcast_adapterThrows_stillRecordsBroadcastAttempt() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } throws Exception("boom")
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Error)
        coVerify { repository.markBroadcastAttempt("account-id", "hash") }
        coVerify { repository.markBroadcastFailed("account-id", "hash", any()) }
    }

    @Test
    fun onBroadcast_unknownErrorMessage_usesGenericSendError() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<TestOfflineTransactionAdapter>()
        coEvery { adapter.broadcastRawTransaction(any(), null) } throws Exception("Error")
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val result = viewModel.uiState.result as? OfflineBroadcastResult.Error
        assertNotNull(result)
        assertTrue(result?.message != "Error")
    }

    @Test
    fun offlineBroadcastErrorText_knownErrors_returnsLocalizedResources() {
        assertErrorTextRes(
            error = Exception(),
            expectedRes = R.string.offline_broadcast_error_send_failed,
        )
        assertErrorTextRes(
            error = Exception("Error"),
            expectedRes = R.string.offline_broadcast_error_send_failed,
        )
        assertErrorTextRes(
            error = UnknownHostException(),
            expectedRes = R.string.Hud_Text_NoInternet,
        )
        assertErrorTextRes(
            error = TimeoutException(),
            expectedRes = R.string.offline_broadcast_error_timeout,
        )
        assertErrorTextRes(
            error = EvmError.RpcError("already known"),
            expectedRes = R.string.offline_broadcast_error_already_sent,
        )
        assertErrorTextRes(
            error = Exception("transaction was committed to the best chain"),
            expectedRes = R.string.offline_broadcast_error_already_sent,
        )
        assertErrorTextRes(
            error = EvmError.RpcError("nonce too low"),
            expectedRes = R.string.offline_broadcast_error_nonce_used,
        )
        assertErrorTextRes(
            error = EvmError.RpcError("transaction underpriced"),
            expectedRes = R.string.offline_broadcast_error_low_fee,
        )
        assertErrorTextRes(
            error = EvmError.RpcError("invalid sender"),
            expectedRes = R.string.offline_broadcast_error_rejected,
        )
    }

    @Test
    fun onBroadcast_adapterNeverReady_showsUnsupportedBlockchain() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        coEvery { adapterManager.awaitAdapterForWallet<IAdapter>(any(), any()) } returns null

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Error)
    }

    @Test
    fun prefillAndAdvance_plainRawHex_isSelectableWithoutEnableAction() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        every { tokenResolver.resolveTokenToEnable(any(), any()) } returns bitcoinToken

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertTrue(confirm?.selectable == true)
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.Send)
        assertNull(confirm?.enableNetworkName)
        coVerify(exactly = 0) { walletUseCase.createWallets(any()) }
    }

    @Test
    fun prefillAndAdvance_plainRawHex_showsSupportedWalletBlockchains() = runTest(dispatcher) {
        val ethereum = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        val ethereumToken = token(ethereum)
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(bitcoinToken, ethereumToken)

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()

        assertEquals(listOf(bitcoin, ethereum), viewModel.uiState.selectableBlockchains)
    }

    @Test
    fun prefillAndAdvance_plainRawHex_doesNotUseMarketWideBlockchains() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(bitcoinToken)

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()

        assertEquals(listOf(bitcoin), viewModel.uiState.selectableBlockchains)
        verify(exactly = 0) { marketKit.allBlockchains() }
    }

    @Test
    fun onSelectBlockchain_plainRawHexMissingBroadcastableWallet_offersEnableNetwork() = runTest(dispatcher) {
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns null
        every { tokenResolver.resolveTokenToEnable(BlockchainType.Bitcoin, account) } returns bitcoinToken

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()

        val confirm = viewModel.uiState.confirm
        assertEquals("Bitcoin", confirm?.blockchainName)
        assertEquals("Bitcoin", confirm?.enableNetworkName)
        assertTrue(confirm?.action is OfflineBroadcastConfirmAction.EnableNetwork)
    }

    @Test
    fun onPrimaryAction_plainRawHexUnsupportedBlockchain_showsUnsupportedError() = runTest(dispatcher) {
        val ethereum = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        val ethereumToken = token(ethereum)
        setActiveWallets(emptyList())
        every { payloadEncoder.decode(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(ethereumToken)
        every { tokenResolver.resolveTokenToEnable(BlockchainType.Ethereum, account) } returns null

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(ethereum)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        val result = viewModel.uiState.result as? OfflineBroadcastResult.Error
        assertEquals("Ethereum", result?.networkName)
        assertNotNull(result?.message)
    }

    private fun createViewModel() = OfflineBroadcastViewModel(
        payloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = repository,
        walletManager = walletManager,
        accountManager = accountManager,
        adapterManager = adapterManager,
        walletUseCase = walletUseCase,
        marketKit = marketKit,
        offlineBroadcastTokenResolver = tokenResolver,
        dispatcherProvider = dispatcherProvider,
    )

    private fun decoded(
        blockchainUid: String = "bitcoin",
        txHash: String = "hash",
        solanaRetryMetadata: OfflineSolanaRetryMetadata? = null,
        tonRetryMetadata: OfflineTonRetryMetadata? = null,
        tronRetryMetadata: OfflineTronRetryMetadata? = null,
        stellarRetryMetadata: OfflineStellarRetryMetadata? = null,
    ) = DecodedOfflineTransaction(
        blockchainUid = blockchainUid,
        rawHex = "deadbeefdeadbeefdead",
        txHash = txHash,
        token = OfflineTokenMetadata(
            tokenQueryId = "$blockchainUid|native",
            coinUid = blockchainUid,
            coinCode = blockchainUid.uppercase(),
            coinName = blockchainUid,
            decimals = 8,
        ),
        amountAtomic = "1000",
        fee = null,
        toAddress = "address",
        createdAt = 0L,
        inputOutpoints = emptyList(),
        solanaRetryMetadata = solanaRetryMetadata,
        tonRetryMetadata = tonRetryMetadata,
        tronRetryMetadata = tronRetryMetadata,
        stellarRetryMetadata = stellarRetryMetadata,
    )

    private fun token(
        blockchain: Blockchain,
        coin: Coin = Coin(uid = blockchain.type.uid, name = blockchain.name, code = blockchain.name),
        tokenType: TokenType = TokenType.Native,
        decimals: Int = 8,
    ) = Token(
        coin = coin,
        blockchain = blockchain,
        type = tokenType,
        decimals = decimals,
    )

    private fun wallet(token: Token, account: Account) = mockk<Wallet>(relaxed = true) {
        every { this@mockk.token } returns token
        every { this@mockk.account } returns account
    }

    private fun assertErrorTextRes(error: Throwable, expectedRes: Int) {
        val message = error.offlineBroadcastErrorText("BNB") as? TranslatableString.ResString
        assertEquals(expectedRes, message?.id)
    }

    private fun mnemonicAccount() = mockk<Account>(relaxed = true) {
        every { isWatchAccount } returns false
        every { type } returns AccountType.Mnemonic(listOf("word"), "")
        every { id } returns "account-id"
    }

    private fun hardwareAccount() = mockk<Account>(relaxed = true) {
        every { isWatchAccount } returns false
        every { isHardwareWalletAccount } returns true
        every { type } returns AccountType.HardwareCard(
            cardId = "card",
            backupCardsCount = 0,
            walletPublicKey = "pub",
            signedHashes = 0,
        )
        every { id } returns "account-id"
    }

    // A public account xPubKey is a watch-only account: isWatchAccount is derived from the real
    // AccountType, so the upfront watch rejection runs against production logic, not a stubbed flag.
    private fun watchAccount() = Account(
        id = "watch-id",
        name = "Watch",
        type = AccountType.HdExtendedKey(WATCH_XPUB),
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private companion object {
        const val WATCH_XPUB =
            "xpub6CudKadFxkN6jXWcJDJSWzt4tNt86ThhYEjtcTywfD5nsYcySEEhfGugKDLnv14ZDNnYBVbfYXbNvRp8cNNw9JAfoMTeph1BqGWYZA4DBDi"
        const val SOLANA_SIGNATURE =
            "7jMAQMhBNsY4eqqGVRYP9ddHbR1vrMvF5qWZbGzMbfyqGzHGmhrxXfQnk74T9JbX8FD9Fyi7Jw1pB8HgZCkP1KKL"
        const val TON_MESSAGE_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val TRON_TX_HASH = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        const val STELLAR_TX_HASH = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        const val MONERO_TX_HASH = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        const val ZCASH_TX_HASH = "456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123"
    }
}

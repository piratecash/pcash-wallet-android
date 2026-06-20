package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.OfflineBroadcastAdapter
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
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

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineBroadcastViewModelTest {

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
            adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any())
        } returns mockk<OfflineBroadcastAdapter>()

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
            adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any())
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
        val adapter = mockk<OfflineBroadcastAdapter>(relaxed = true)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()

        // Account turns watch-only before the user taps send.
        every { accountManager.activeAccount } returns watchAccount()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.dismissError)
        coVerify(exactly = 0) { adapter.broadcastRawTransaction(any()) }
    }

    @Test
    fun onBroadcast_adapterReady_submitsAndShowsSuccess() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<OfflineBroadcastAdapter>()
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals(OfflineBroadcastStep.Result, viewModel.uiState.step)
        assertTrue(viewModel.uiState.result is OfflineBroadcastResult.Success)
        coVerify { adapter.broadcastRawTransaction("deadbeefdeadbeefdead") }
    }

    @Test
    fun onBroadcast_adapterQueues_keepsRecordPendingAndShowsQueuedSuccess() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<OfflineBroadcastAdapter>()
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Queued)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

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
    }

    @Test
    fun onBroadcast_plainRawHexSubmitted_persistsBroadcastedRawTransaction() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        val adapter = mockk<OfflineBroadcastAdapter>()
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("derived-hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerifyOrder {
            repository.saveImported(
                bitcoinWallet,
                match {
                    it.rawHex == "deadbeefdeadbeefdead" &&
                        it.txHash == "derived-hash" &&
                        it.amountAtomic.isEmpty() &&
                        it.toAddress.isEmpty()
                },
                "",
            )
            repository.markBroadcastAttempt("account-id", "derived-hash")
            repository.markBroadcasted("account-id", "derived-hash", "derived-hash")
        }
    }

    @Test
    fun onBroadcast_plainRawHexQueued_persistsPendingRawTransaction() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns null
        val adapter = mockk<OfflineBroadcastAdapter>()
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("queued-hash", BroadcastRawTransactionStatus.Queued)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("deadbeefdeadbeefdead")
        advanceUntilIdle()
        viewModel.onSelectBlockchain(bitcoin)
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerifyOrder {
            repository.saveImported(
                bitcoinWallet,
                match {
                    it.rawHex == "deadbeefdeadbeefdead" &&
                        it.txHash == "queued-hash" &&
                        it.amountAtomic.isEmpty() &&
                        it.toAddress.isEmpty()
                },
                "",
            )
            repository.markBroadcastAttempt("account-id", "queued-hash")
        }
        coVerify(exactly = 0) { repository.markBroadcasted(any(), any(), any()) }
    }

    @Test
    fun onBroadcast_importStillSaving_persistsImportBeforeMarkingBroadcast() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<OfflineBroadcastAdapter>()
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter
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
        }
    }

    @Test
    fun onBroadcast_adapterDerivesDifferentTxHash_reconcilesRecordToDerivedHash() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        val adapter = mockk<OfflineBroadcastAdapter>()
        // The payload claimed "hash"; the kit derives the real txid from the raw bytes.
        coEvery { adapter.broadcastRawTransaction(any()) } returns
            BroadcastRawTransactionResult("derived-hash", BroadcastRawTransactionStatus.Submitted)
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns adapter

        val viewModel = createViewModel()
        viewModel.prefillAndAdvance("pcash:tx:v1:payload")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        coVerify { repository.markBroadcasted("account-id", "hash", "derived-hash") }
    }

    @Test
    fun onBroadcast_adapterNeverReady_showsUnsupportedBlockchain() = runTest(dispatcher) {
        setActiveWallets(listOf(bitcoinWallet))
        every { payloadEncoder.decode(any()) } returns decoded()
        every { marketKit.blockchain("bitcoin") } returns bitcoin
        coEvery { adapterManager.awaitAdapterForWallet<OfflineBroadcastAdapter>(any(), any()) } returns null

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

    private fun decoded(blockchainUid: String = "bitcoin") = DecodedOfflineTransaction(
        blockchainUid = blockchainUid,
        rawHex = "deadbeefdeadbeefdead",
        txHash = "hash",
        amountAtomic = "1000",
        feeAtomic = null,
        toAddress = "address",
        createdAt = 0L,
        inputOutpoints = emptyList(),
    )

    private fun token(blockchain: Blockchain) = Token(
        coin = Coin(uid = blockchain.type.uid, name = blockchain.name, code = blockchain.name),
        blockchain = blockchain,
        type = TokenType.Native,
        decimals = 8,
    )

    private fun wallet(token: Token, account: Account) = mockk<Wallet>(relaxed = true) {
        every { this@mockk.token } returns token
        every { this@mockk.account } returns account
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
    }
}

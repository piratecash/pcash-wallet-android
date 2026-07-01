package cash.p.terminal.modules.send.stellar

import cash.p.terminal.core.ISendStellarAdapter
import cash.p.terminal.core.OfflineStellarSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineStellarTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendStellarViewModelTest : KoinTest {

    private interface TestSendStellarAdapter : ISendStellarAdapter, OfflineTransactionAdapter<SignedOfflineStellarTransaction>

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<TestSendStellarAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val amountValidator = mockk<AmountValidator>(relaxed = true)

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.StellarSecretKey("secret"),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val stellarToken = Token(
        coin = Coin(uid = "stellar", name = "Stellar", code = "XLM"),
        blockchain = stellar,
        type = TokenType.Native,
        decimals = 7,
    )
    private val wallet = createWallet(stellarToken)
    private val amount = BigDecimal("1.2")
    private val address = Address("GAZXDMWYHMPM2WF6FCWEBIMJITKKTU6MLHYLCFRVB3WMXTNPVEHBOXRE")

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single<MarketKitWrapper> { marketKit }
                single { poisonAddressManager }
                single { locallyCreatedTransactionRepository }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { adapter.sendFee } returns SEND_FEE
        coEvery { adapter.getMinimumSendAmount(any()) } returns null
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { amountValidator.validate(any(), any(), any(), any(), any()) } returns null
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.signOffline(any()) } returns signedTransaction()
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_nativeTransaction_savesStellarRetryMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(stellarToken, draft.wallet.token)
        assertEquals(stellarToken, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(STELLAR_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(SEND_FEE, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        assertTrue(draft.inputOutpoints.isEmpty())
        assertEquals("GSource", draft.stellarRetryMetadata?.sourceAccountId)
        assertEquals(123_456_789L, draft.stellarRetryMetadata?.sequenceNumber)
        assertEquals(1_700_000_180L, draft.stellarRetryMetadata?.validUntil)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineStellarSignRequest &&
                        it.amount == amount &&
                        it.address == address.hex &&
                        it.memo == "memo"
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun onClickSignOffline_assetTransaction_savesAssetWalletAndXlmFeeToken() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val assetToken = assetToken()
        val assetWallet = createWallet(assetToken)
        val viewModel = createViewModel(
            wallet = assetWallet,
            feeToken = stellarToken,
        )
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(assetToken, draft.wallet.token)
        assertEquals(stellarToken, draft.feeToken)
        assertEquals(SEND_FEE, draft.fee)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineStellarSignRequest &&
                        it.amount == amount &&
                        it.address == address.hex
                }
            )
        }
    }

    @Test
    fun onClickSignOffline_userCancelled_resetsStateSilently() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws TrezorCancelledException()
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertEquals(OfflineSignState.Idle, viewModel.offlineSignState)
        coVerify(exactly = 0) { offlineSignedTransactionRepository.save(any(), any()) }
    }

    @Test
    fun offlineSignSupported_watchAccount_returnsFalse() {
        val watchAccount = account(AccountType.StellarAddress(address.hex))
        val watchWallet = createWallet(stellarToken, watchAccount)

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        feeToken: Token = stellarToken,
    ) = SendStellarViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        coinMaxAllowedDecimals = wallet.token.decimals,
        xRateService = xRateService,
        address = null,
        showAddressInput = true,
        amountService = SendAmountService(amountValidator, wallet.coin.code, BigDecimal.TEN),
        addressService = SendStellarAddressService(),
        contactsRepo = contactsRepository,
        minimumAmountService = SendStellarMinimumAmountService(adapter),
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        recentAddressManager = recentAddressManager,
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun signedTransaction() = SignedOfflineStellarTransaction(
        rawHex = "deadbeef",
        txHash = STELLAR_TX_HASH,
        fee = SEND_FEE,
        sourceAccountId = "GSource",
        sequenceNumber = 123_456_789L,
        validUntil = 1_700_000_180L,
    )

    private fun assetToken() = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = stellar,
        type = TokenType.Asset("USDC", "GIssuer"),
        decimals = 7,
    )

    private fun createWallet(token: Token, account: Account = this.account) =
        checkNotNull(walletFactory.create(token, account, null))

    private fun account(type: AccountType) = Account(
        id = "account-${type::class.simpleName}",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        val SEND_FEE: BigDecimal = BigDecimal("0.00001")
        const val STELLAR_TX_HASH = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    }
}

package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.SignedOfflineZcashTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.BalanceData
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendZCashViewModelTest : KoinTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<ISendZcashAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private val feeFlow = MutableStateFlow(FEE)
    private val balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val zcash = Blockchain(BlockchainType.Zcash, "Zcash", null)
    private val zcashToken = Token(
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        blockchain = zcash,
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        decimals = 8,
    )
    private val account = account(AccountType.Mnemonic(List(12) { "word$it" }, ""))
    private val wallet = createWallet(zcashToken, account)
    private val amount = BigDecimal("1.2")
    private val address = Address(ZCASH_ADDRESS)

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

        every { adapter.fee } returns feeFlow
        every { adapter.balanceData } returns BalanceData(BigDecimal.TEN)
        every { adapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Shielded
        coEvery { adapter.signOffline(any()) } returns signedTransaction()
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { adapterManager.getAdjustedBalanceData(any()) } returns null
        every { adapterManager.getBalanceAdapterForWallet(wallet) } returns adapter
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_validTransaction_savesDraft() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
        val draft = draftSlot.captured
        assertEquals(zcashToken, draft.wallet.token)
        assertEquals(zcashToken, draft.feeToken)
        assertEquals("deadbeefdeadbeefdead", draft.rawHex)
        assertEquals(ZCASH_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(FEE, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        assertTrue(draft.inputOutpoints.isEmpty())
        assertEquals(null, draft.solanaRetryMetadata)
        assertEquals(null, draft.tonRetryMetadata)
        assertEquals(null, draft.tronRetryMetadata)
        assertEquals(null, draft.stellarRetryMetadata)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineZcashSignRequest &&
                        it.amount == amount &&
                        it.address == ZCASH_ADDRESS &&
                        it.memo == "memo"
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun offlineSignSupported_mnemonicAccount_returnsTrue() {
        val viewModel = createViewModel()

        assertTrue(viewModel.offlineSignSupported)
    }

    @Test
    fun offlineSignSupported_ufvkAccount_returnsFalse() {
        val ufvkWallet = createWallet(zcashToken, account(AccountType.ZCashUfvKey("ufvk")))

        val viewModel = createViewModel(wallet = ufvkWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    private fun createViewModel(wallet: Wallet = this.wallet) = SendZCashViewModel(
        adapter = adapter,
        wallet = wallet,
        xRateService = xRateService,
        amountService = SendAmountService(AmountValidator(), wallet.coin.code, BigDecimal.TEN),
        addressService = SendZCashAddressService(adapter),
        memoService = SendZCashMemoService(),
        contactsRepo = contactsRepository,
        address = null,
        showAddressInput = true,
        pendingRegistrar = pendingRegistrar,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun signedTransaction() = SignedOfflineZcashTransaction(
        rawHex = "deadbeefdeadbeefdead",
        txHash = ZCASH_TX_HASH,
        fee = FEE,
    )

    private fun assertSigned(viewModel: SendZCashViewModel) {
        when (val state = viewModel.offlineSignState) {
            is OfflineSignState.Signed -> Unit
            is OfflineSignState.Failed -> fail(state.caution.s.toString())
            else -> fail(state.toString())
        }
    }

    private fun createWallet(token: Token, account: Account) =
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
        const val ZCASH_ADDRESS = "zs1recipient000000000000000000000000000000000000000000000000000000000000"
        const val ZCASH_TX_HASH = "456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123"
        val FEE: BigDecimal = BigDecimal("0.0001")
    }
}

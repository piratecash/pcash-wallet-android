package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTronSignRequest
import cash.p.terminal.core.SignedOfflineTronTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
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
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.tronkit.transaction.Fee
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal
import io.horizontalsystems.tronkit.models.Address as TronAddress

@OptIn(ExperimentalCoroutinesApi::class)
class SendTronViewModelTest : KoinTest {

    private interface TestSendTronAdapter : ISendTronAdapter, OfflineTransactionAdapter<SignedOfflineTronTransaction>

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<TestSendTronAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val amountService = mockk<SendAmountService>(relaxed = true)
    private val addressService = mockk<SendTronAddressService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private lateinit var amountStateFlow: MutableStateFlow<SendAmountService.State>
    private lateinit var addressStateFlow: MutableStateFlow<SendTronAddressService.State>

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
    private val tron = Blockchain(BlockchainType.Tron, "TRON", null)
    private val trxToken = Token(
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        blockchain = tron,
        type = TokenType.Native,
        decimals = 6,
    )
    private val wallet = createWallet(trxToken)
    private val amount = BigDecimal("1.2")
    private val address = Address(TRON_ADDRESS)

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

        amountStateFlow = MutableStateFlow(createAmountState())
        addressStateFlow = MutableStateFlow(createAddressState())

        every { amountService.stateFlow } returns amountStateFlow
        every { addressService.stateFlow } returns addressStateFlow
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.estimateFee(any(), any()) } returns listOf(Fee.Energy(required = 10, price = 2))
        coEvery { adapter.signOffline(any()) } returns SignedOfflineTronTransaction(
            rawHex = "deadbeef",
            txHash = TRON_TX_HASH,
            expiration = EXPIRATION,
        )
        every { amountService.setAmount(any()) } answers {
            val value = firstArg<BigDecimal?>()
            amountStateFlow.value = createAmountState(
                amount = value,
                canBeSend = value != null && value > BigDecimal.ZERO,
            )
        }
        coEvery { addressService.setAddress(any()) } coAnswers {
            val value = firstArg<Address?>()
            addressStateFlow.value = createAddressState(
                address = value,
                canBeSend = value != null,
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_nativeTransaction_savesTronRetryMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()
        viewModel.onNavigateToConfirmation()
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
        val draft = draftSlot.captured
        assertEquals(trxToken, draft.wallet.token)
        assertEquals(trxToken, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(TRON_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
        assertEquals(EXPIRATION, draft.tronRetryMetadata?.expiration)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineTronSignRequest &&
                        it.amount == amount &&
                        it.address.base58 == TRON_ADDRESS &&
                        it.feeLimit == null
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun onClickSignOffline_trc20Transaction_passesFeeLimitAndSavesTrc20Wallet() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val usdtToken = trc20Token()
        val viewModel = createViewModel(wallet = createWallet(usdtToken))
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()
        viewModel.onNavigateToConfirmation()
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
        val draft = draftSlot.captured
        assertEquals(usdtToken, draft.wallet.token)
        assertEquals(trxToken, draft.feeToken)
        assertEquals(EXPIRATION, draft.tronRetryMetadata?.expiration)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineTronSignRequest &&
                        it.amount == amount &&
                        it.address.base58 == TRON_ADDRESS &&
                        it.feeLimit == 20L
                }
            )
        }
    }

    @Test
    fun offlineSignSupported_watchAccount_returnsFalse() {
        val watchWallet = createWallet(trxToken, account(AccountType.TronAddress(TRON_ADDRESS)))

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        feeToken: Token = trxToken,
    ) = SendTronViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = wallet.token.decimals,
        contactsRepo = contactsRepository,
        showAddressInput = true,
        connectivityManager = connectivityManager,
        address = null,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        recentAddressManager = recentAddressManager,
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun assertSigned(viewModel: SendTronViewModel) {
        when (val state = viewModel.offlineSignState) {
            is OfflineSignState.Signed -> Unit
            is OfflineSignState.Failed -> fail(state.caution.s.toString())
            else -> fail(state.toString())
        }
    }

    private fun createAmountState(
        amount: BigDecimal? = null,
        canBeSend: Boolean = false,
    ) = SendAmountService.State(
        amount = amount,
        amountCaution = null,
        availableBalance = BigDecimal.TEN,
        canBeSend = canBeSend,
    )

    private fun createAddressState(
        address: Address? = null,
        canBeSend: Boolean = false,
    ) = SendTronAddressService.State(
        address = address,
        tronAddress = address?.let { TronAddress.fromBase58(it.hex) },
        addressError = null,
        isInactiveAddress = false,
        canBeSend = canBeSend,
    )

    private fun trc20Token() = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        blockchain = tron,
        type = TokenType.Eip20(TRC20_CONTRACT),
        decimals = 6,
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
        const val TRON_ADDRESS = "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7"
        const val TRC20_CONTRACT = "TXLAQ63Xg1NAzckPwKHvzw7CSEmLMEqcdj"
        const val TRON_TX_HASH = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        const val EXPIRATION = 1_700_000_060_000L
    }
}

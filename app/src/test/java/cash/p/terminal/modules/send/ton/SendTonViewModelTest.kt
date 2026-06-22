package cash.p.terminal.modules.send.ton

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.OfflineTonSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineTonTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
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
import com.tangem.common.core.TangemSdkError
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.tonkit.FriendlyAddress
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendTonViewModelTest : KoinTest {

    private interface TestSendTonAdapter : ISendTonAdapter, OfflineTransactionAdapter<SignedOfflineTonTransaction>

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<TestSendTonAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val amountService = mockk<SendTonAmountService>(relaxed = true)
    private val addressService = mockk<SendTonAddressService>(relaxed = true)
    private val feeService = mockk<SendTonFeeService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private lateinit var amountStateFlow: MutableStateFlow<SendTonAmountService.State>
    private lateinit var addressStateFlow: MutableStateFlow<SendTonAddressService.State>
    private lateinit var feeStateFlow: MutableStateFlow<SendTonFeeService.State>

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
    private val ton = Blockchain(BlockchainType.Ton, "TON", null)
    private val tonToken = Token(
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        blockchain = ton,
        type = TokenType.Native,
        decimals = 9,
    )
    private val wallet = checkNotNull(walletFactory.create(tonToken, account, null))
    private val amount = BigDecimal("1.2")
    private val address = Address(VALID_TON_ADDRESS)
    private val friendlyAddress = FriendlyAddress.parse(VALID_TON_ADDRESS)

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
        feeStateFlow = MutableStateFlow(createFeeState())

        every { amountService.stateFlow } returns amountStateFlow
        every { addressService.stateFlow } returns addressStateFlow
        every { feeService.stateFlow } returns feeStateFlow
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.signOffline(any()) } returns SignedOfflineTonTransaction(
            rawHex = "deadbeef",
            txHash = TON_MESSAGE_HASH,
            fee = BigDecimal("0.01"),
            validUntil = 1_700_000_300L,
            senderAddress = "EQSender",
            seqno = 7,
        )
        every { amountService.setAmount(any()) } answers {
            val value = firstArg<BigDecimal?>()
            amountStateFlow.value = createAmountState(
                amount = value,
                canBeSend = value != null && value > BigDecimal.ZERO,
            )
        }
        every { addressService.setAddress(any()) } answers {
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
    fun onClickSignOffline_tonTransaction_savesTonRetryMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(tonToken, draft.wallet.token)
        assertEquals(tonToken, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(TON_MESSAGE_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
        assertEquals(BigDecimal("0.01"), draft.fee)
        assertEquals(1_700_000_300L, draft.tonRetryMetadata?.validUntil)
        assertEquals("EQSender", draft.tonRetryMetadata?.senderAddress)
        assertEquals(7, draft.tonRetryMetadata?.seqno)
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineTonSignRequest &&
                        it.amount == amount &&
                        it.address.addrStd.toString(userFriendly = false) ==
                            friendlyAddress.addrStd.toString(userFriendly = false) &&
                        it.address.isBounceable == friendlyAddress.isBounceable &&
                        it.memo == null
                }
            )
        }
    }

    @Test
    fun onClickSignOffline_jettonTransaction_savesJettonWalletAndNativeFeeToken() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val jettonToken = jettonToken()
        val jettonWallet = createWallet(jettonToken)
        val viewModel = createViewModel(
            wallet = jettonWallet,
            feeToken = tonToken,
        )
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(jettonToken, draft.wallet.token)
        assertEquals(tonToken, draft.feeToken)
        assertEquals(BigDecimal("0.01"), draft.fee)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
    }

    @Test
    fun onClickSignOffline_userCancelled_resetsStateSilently() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws TangemSdkError.UserCancelled()
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
        val watchAccount = account(AccountType.TonAddress(VALID_TON_ADDRESS))
        val watchWallet = createWallet(tonToken, watchAccount)

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        feeToken: Token = tonToken,
    ) = SendTonViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        feeService = feeService,
        coinMaxAllowedDecimals = wallet.token.decimals,
        contactsRepo = contactsRepository,
        showAddressInput = true,
        address = null,
        pendingRegistrar = pendingRegistrar,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        recentAddressManager = recentAddressManager,
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun createAmountState(
        amount: BigDecimal? = null,
        canBeSend: Boolean = false,
    ) = SendTonAmountService.State(
        amount = amount,
        memo = null,
        amountCaution = null,
        availableBalance = BigDecimal.TEN,
        canBeSend = canBeSend,
    )

    private fun createAddressState(
        address: Address? = null,
        canBeSend: Boolean = false,
    ) = SendTonAddressService.State(
        address = address,
        tonAddress = address?.let { FriendlyAddress.parse(it.hex) },
        addressError = null,
        canBeSend = canBeSend,
    )

    private fun createFeeState() = SendTonFeeService.State(
        feeStatus = FeeStatus.Success(BigDecimal("0.01")),
        inProgress = false,
    )

    private fun jettonToken() = Token(
        coin = Coin(uid = "jetton", name = "Jetton", code = "JET"),
        blockchain = ton,
        type = TokenType.Jetton("EQJetton"),
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
        const val VALID_TON_ADDRESS = "UQCYTBH7n8OnQ6BgOfdkNRWF7socLJb9U-JMRcoz3UpL_0V6"
        const val TON_MESSAGE_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}

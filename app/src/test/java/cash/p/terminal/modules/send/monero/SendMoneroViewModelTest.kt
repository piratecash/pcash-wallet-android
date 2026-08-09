package cash.p.terminal.modules.send.monero

import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.OfflineMoneroSignRequest
import cash.p.terminal.core.SignedOfflineMoneroTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
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
import io.mockk.clearMocks
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
import kotlinx.coroutines.test.TestScope
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
import java.net.UnknownHostException
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.send.mockConnectivityManager

@OptIn(ExperimentalCoroutinesApi::class)
class SendMoneroViewModelTest : KoinTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<ISendMoneroAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val amountService = mockk<SendAmountService>(relaxed = true)
    private val addressService = mockk<SendMoneroAddressService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private lateinit var amountStateFlow: MutableStateFlow<SendAmountService.State>
    private lateinit var addressStateFlow: MutableStateFlow<SendMoneroAddressService.State>
    private lateinit var balanceUpdatedFlow: MutableSharedFlow<Unit>

    // Production wires both connectivity reads to one singleton, so the test must too.
    private val isConnected = MutableStateFlow(true)

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
    private val monero = Blockchain(BlockchainType.Monero, "Monero", null)
    private val moneroToken = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = monero,
        type = TokenType.Native,
        decimals = 12,
    )
    private val wallet = createWallet(moneroToken)
    private val amount = BigDecimal("1.2")
    private val address = Address(MONERO_ADDRESS)

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IConnectivityManager> { mockConnectivityManager(isConnected) }
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
        balanceUpdatedFlow = MutableSharedFlow(extraBufferCapacity = 1)

        every { amountService.stateFlow } returns amountStateFlow
        every { addressService.stateFlow } returns addressStateFlow
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { connectivityManager.isConnected } returns isConnected
        every { adapter.balanceData } returns BalanceData(BigDecimal.TEN)
        every { adapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        every { adapterManager.getAdjustedBalanceData(any()) } returns null
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.estimateFee(any(), any(), any()) } returns FEE
        coEvery { adapter.send(any(), any(), any()) } returns MONERO_TX_HASH
        coEvery { locallyCreatedTransactionRepository.markCreated(any<Wallet>(), any()) } returns Unit
        coEvery { adapter.signOffline(any()) } returns signedTransaction()
        every { amountService.setAmount(any()) } answers {
            val value = firstArg<BigDecimal?>()
            amountStateFlow.value = createAmountState(
                amount = value,
                availableBalance = amountStateFlow.value.availableBalance,
                canBeSend = value != null && value > BigDecimal.ZERO,
            )
        }
        every { amountService.updateAvailableBalance(any()) } answers {
            val value = firstArg<BigDecimal>()
            val currentState = amountStateFlow.value
            amountStateFlow.value = createAmountState(
                amount = currentState.amount,
                availableBalance = value,
                canBeSend = currentState.amount != null && currentState.amount > BigDecimal.ZERO,
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
        assertEquals(moneroToken, draft.wallet.token)
        assertEquals(null, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(MONERO_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(SIGNED_FEE, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        assertEquals(null, draft.solanaRetryMetadata)
        assertEquals(null, draft.tonRetryMetadata)
        assertEquals(null, draft.tronRetryMetadata)
        assertEquals(null, draft.stellarRetryMetadata)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineMoneroSignRequest &&
                        it.amount == amount &&
                        it.address == MONERO_ADDRESS &&
                        it.memo == "memo"
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun onBalanceUpdated_updatesAvailableBalanceFromMaxSpendable() = runTest(dispatcher) {
        val viewModel = createViewModel()
        every { adapter.maxSpendableBalance } returns BigDecimal("4")

        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()

        assertEquals(BigDecimal("4"), viewModel.uiState.availableBalance)
    }

    @Test
    fun onClickSend_memoEntered_passesMemoToEstimateAndSend() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()
        clearMocks(adapter, answers = false)

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(SendResult.Sent(MONERO_TX_HASH), viewModel.sendResult)
        coVerify(exactly = 1) { adapter.estimateFee(amount, MONERO_ADDRESS, "memo") }
        coVerify(exactly = 1) { adapter.send(amount, MONERO_ADDRESS, "memo") }
        coVerify { locallyCreatedTransactionRepository.markCreated(wallet, MONERO_TX_HASH) }
    }

    @Test
    fun onClickSend_amountAboveUnlockedBalance_failsWithoutSending() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal("4")
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(BigDecimal("5"))
        advanceUntilIdle()
        clearMocks(adapter, answers = false)

        viewModel.onClickSend()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Failed)
        coVerify(exactly = 1) { adapter.estimateFee(BigDecimal("5"), MONERO_ADDRESS, null) }
        coVerify(exactly = 0) { adapter.send(any(), any(), any()) }
    }

    @Test
    fun offlineSignSupported_supportedAdapter_returnsTrue() {
        val viewModel = createViewModel()

        assertTrue(viewModel.offlineSignSupported)
    }

    @Test
    fun offlineSignSupported_watchAccount_returnsFalse() {
        val watchWallet = watchWallet()

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    @Test
    fun onClickSignOffline_preparedOnlineThenConnectionLost_signsFromCache() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        goOfflineWithBalanceUpdate()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
    }

    @Test
    fun balanceUpdate_sameInputs_doesNotRebuildSignedTransaction() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        every { adapter.maxSpendableBalance } returns BigDecimal("4")
        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.signOffline(any()) }
    }

    @Test
    fun amountChanged_rebuildsSignedTransactionForNewAmount() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onEnterAmount(BigDecimal("2.5"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            adapter.signOffline(match { it is OfflineMoneroSignRequest && it.amount == BigDecimal("2.5") })
        }
    }

    @Test
    fun onClickSignOffline_prepareFailedThenOffline_reportsBuildError() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws IllegalStateException(BUILD_ERROR)
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        goOfflineWithBalanceUpdate()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val state = viewModel.offlineSignState as OfflineSignState.Failed
        assertEquals(BUILD_ERROR, (state.caution.s as TranslatableString.PlainString).text)
    }

    @Test
    fun getConfirmationData_transactionPrepared_usesFeeFromSignedTransaction() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        goOfflineWithBalanceUpdate()

        assertEquals(SIGNED_FEE, viewModel.getConfirmationData().fee)
    }

    @Test
    fun prepareFailedThenBalanceUpdate_online_retriesPreparation() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws IllegalStateException(BUILD_ERROR)
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        coEvery { adapter.signOffline(any()) } returns signedTransaction()
        every { adapter.maxSpendableBalance } returns BigDecimal("4")
        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
    }

    // Losing the daemon makes fee estimation fail and the kit re-emits the balance.
    private suspend fun TestScope.goOfflineWithBalanceUpdate() {
        isConnected.value = false
        coEvery { adapter.estimateFee(any(), any(), any()) } throws UnknownHostException()
        every { adapter.maxSpendableBalance } returns BigDecimal("4")
        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()
    }

    private fun signedTransaction() = SignedOfflineMoneroTransaction(
        rawHex = "deadbeef",
        txHash = MONERO_TX_HASH,
        fee = SIGNED_FEE,
    )

    private fun createViewModel(wallet: Wallet = this.wallet) = SendMoneroViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = wallet.token.decimals,
        showAddressInput = true,
        contactsRepo = contactsRepository,
        connectivityManager = connectivityManager,
        address = null,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun assertSigned(viewModel: SendMoneroViewModel) {
        when (val state = viewModel.offlineSignState) {
            is OfflineSignState.Signed -> Unit
            is OfflineSignState.Failed -> fail(state.caution.s.toString())
            else -> fail(state.toString())
        }
    }

    private fun createAmountState(
        amount: BigDecimal? = null,
        availableBalance: BigDecimal = BigDecimal.TEN,
        canBeSend: Boolean = false,
    ) = SendAmountService.State(
        amount = amount,
        amountCaution = null,
        availableBalance = availableBalance,
        canBeSend = canBeSend,
    )

    private fun createAddressState(
        address: Address? = null,
        canBeSend: Boolean = false,
    ) = SendMoneroAddressService.State(
        address = address,
        addressError = null,
        canBeSend = canBeSend,
    )

    private fun createWallet(token: Token, account: Account = this.account) =
        checkNotNull(walletFactory.create(token, account, null))

    private fun watchWallet() = mockk<Wallet>(relaxed = true) {
        every { token } returns moneroToken
        every { coin } returns moneroToken.coin
        every { account } returns mockk {
            every { isWatchAccount } returns true
            every { id } returns "watch-account"
        }
    }

    private companion object {
        const val MONERO_ADDRESS =
            "84jsRecipienT111111111111111111111111111111111111111111111111111111111111111"
        const val MONERO_TX_HASH = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        const val BUILD_ERROR = "not enough outputs"
        val FEE: BigDecimal = BigDecimal("0.000123456789")

        // Distinct from FEE so tests can tell the estimate apart from the signed transaction's fee.
        val SIGNED_FEE: BigDecimal = BigDecimal("0.000222222222")
    }
}

package cash.p.terminal.modules.paycore.payment

import cash.p.terminal.R
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreAmountType
import cash.p.terminal.modules.paycore.PayCorePaymentCalculationRequest
import cash.p.terminal.modules.paycore.PayCorePaymentCalculationResponse
import cash.p.terminal.modules.paycore.PayCorePaymentCreateResponse
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.PayCoreWalletApprovalResult
import cash.p.terminal.modules.paycore.PayCoreWalletApprovalService
import cash.p.terminal.modules.paycore.PayCoreWalletNotApprovedException
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.UnknownHostException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PayCorePaymentViewModelTest {

    private val apiService = mockk<PayCoreApiService>()
    private val walletApprovalService = mockk<PayCoreWalletApprovalService>()
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true).also {
        every { it.activeAccount } returns null
    }
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { walletApprovalService.ensureApprovedForSavedPhone(any(), any()) } returns Unit
        coEvery { apiService.calculatePayment(any(), any()) } returns PayCorePaymentCalculationResponse(
            amountCrypto = BigDecimal("20"),
            fullAmountRub = BigDecimal("2"),
            ticker = "USDT",
            uuid = "calculation-uuid",
            expiresAt = "2026-06-05T00:00:30Z"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onConfirm_successCreatesPaymentFromCalculationUuid() = runTest(dispatcher) {
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/payment/payment-123",
            uuid = "payment-123",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals("payment-123", viewModel.uiState.paymentId)
        assertEquals("https://pirate.paycore.pw/payment/payment-123", viewModel.uiState.paymentUrl)
        assertTrue(viewModel.uiState.showWebView)
        coVerifyOrder {
            walletApprovalService.ensureApprovedForSavedPhone("TUserUsdtAddress", PayCoreTicker.USDT)
            apiService.calculatePayment(any(), PayCoreTicker.USDT)
            apiService.createPayment(match { it.uuid == "calculation-uuid" }, PayCoreTicker.USDT)
        }
    }

    @Test
    fun onConfirm_networkError_showsNoInternetMessage() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any(), any()) } throws IllegalStateException(
            "host.not.found",
            UnknownHostException()
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals(Translator.getString(R.string.Hud_Text_NoInternet), viewModel.uiState.error)
    }

    @Test
    fun onConfirm_walletNotApproved_doesNotCreatePayment() = runTest(dispatcher) {
        coEvery {
            walletApprovalService.ensureApprovedForSavedPhone(any(), any())
        } throws PayCoreWalletNotApprovedException(PayCoreWalletApprovalResult.Pending)

        val viewModel = createViewModel()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals(Translator.getString(R.string.paycore_verification_error), viewModel.uiState.error)
        coVerify(exactly = 0) { apiService.createPayment(any(), any()) }
    }

    @Test
    fun onConfirm_paymentIdMissing_showsErrorAndDoesNotOpenWebView() = runTest(dispatcher) {
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertNull(viewModel.uiState.paymentId)
        assertNull(viewModel.uiState.paymentUrl)
        assertEquals(Translator.getString(R.string.Error), viewModel.uiState.error)
        coVerify(exactly = 0) { storage.saveAsync(any()) }
    }

    @Test
    fun onConfirm_paymentUrlMissing_showsErrorAndDoesNotOpenWebView() = runTest(dispatcher) {
        givenPaymentCreateResponse(
            paymentUrl = "",
            uuid = "payment-123",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertNull(viewModel.uiState.paymentId)
        assertNull(viewModel.uiState.paymentUrl)
        assertEquals(Translator.getString(R.string.Error), viewModel.uiState.error)
        coVerify(exactly = 0) { storage.saveAsync(any()) }
    }

    @Test
    fun onWebViewOpened_success_savesCalculatedAmounts() = runTest(dispatcher) {
        val transactionSlot = slot<SwapProviderTransaction>()
        givenActiveAccount()
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )
        coEvery { storage.saveAsync(capture(transactionSlot)) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewOpened()
        advanceUntilIdle()

        assertEquals(0, transactionSlot.captured.amountIn.compareTo(BigDecimal("2")))
        assertEquals(0, transactionSlot.captured.amountOut.compareTo(BigDecimal("20")))
        assertEquals(TransactionStatusEnum.WAITING.name.lowercase(), transactionSlot.captured.status)
    }

    @Test
    fun onWebViewOpened_calledTwice_savesTransactionOnce() = runTest(dispatcher) {
        givenActiveAccount()
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewOpened()
        viewModel.onWebViewOpened()
        viewModel.onWebViewCompleted()
        advanceUntilIdle()

        coVerify(exactly = 1) { storage.saveAsync(any()) }
        assertTrue(viewModel.uiState.completed)
    }

    @Test
    fun onWebViewOpenedAndCompleted_parallelSave_usesSameTransactionDate() = runTest(dispatcher) {
        val transactions = mutableListOf<SwapProviderTransaction>()
        givenActiveAccount()
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )
        coEvery { storage.saveAsync(capture(transactions)) } coAnswers {
            delay(100)
            Unit
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewOpened()
        viewModel.onWebViewCompleted()
        advanceUntilIdle()

        assertEquals(2, transactions.size)
        assertEquals(transactions[0].date, transactions[1].date)
        assertTrue(viewModel.uiState.completed)
    }

    @Test
    fun onWebViewCompleted_saveFails_doesNotCompleteAndShowsError() = runTest(dispatcher) {
        givenActiveAccount()
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )
        coEvery { storage.saveAsync(any()) } throws IllegalStateException("db error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewCompleted()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.completed)
        assertFalse(viewModel.uiState.showWebView)
        assertEquals(Translator.getString(R.string.Error), viewModel.uiState.error)
    }

    @Test
    fun onConfirm_completionPendingAfterSaveFails_retriesSaveAndCompletes() = runTest(dispatcher) {
        givenActiveAccount()
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )
        coEvery {
            storage.saveAsync(any())
        } throws IllegalStateException("db error") andThen Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewCompleted()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.completed)
        assertEquals(Translator.getString(R.string.Error), viewModel.uiState.error)

        viewModel.onConfirm()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.completed)
        assertFalse(viewModel.uiState.showWebView)
        assertNull(viewModel.uiState.error)
        coVerify(exactly = 2) { storage.saveAsync(any()) }
        coVerify(exactly = 1) { apiService.createPayment(any(), any()) }
    }

    @Test
    fun onWebViewClosed_existingPayment_hidesWebViewAndKeepsPayment() = runTest(dispatcher) {
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewClosed()

        assertFalse(viewModel.uiState.showWebView)
        assertEquals("payment-123", viewModel.uiState.paymentId)
        assertEquals("https://pirate.paycore.pw/pay", viewModel.uiState.paymentUrl)
    }

    @Test
    fun onConfirm_existingPayment_reopensWebViewWithoutCreatingNewPayment() = runTest(dispatcher) {
        givenPaymentCreateResponse(
            paymentUrl = "https://pirate.paycore.pw/pay",
            uuid = "payment-123",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewClosed()
        viewModel.onConfirm()

        assertTrue(viewModel.uiState.showWebView)
        assertEquals("payment-123", viewModel.uiState.paymentId)
        coVerify(exactly = 1) { apiService.createPayment(any(), any()) }
    }

    @Test
    fun onConfirm_existingPaymentExpired_createsNewPayment() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any(), any()) } returnsMany listOf(
            PayCorePaymentCreateResponse(
                paymentUrl = "https://pirate.paycore.pw/pay/expired",
                uuid = "expired-payment",
                expiresAt = pastExpiresAt(),
            ),
            PayCorePaymentCreateResponse(
                paymentUrl = "https://pirate.paycore.pw/pay/fresh",
                uuid = "fresh-payment",
                expiresAt = futureExpiresAt(),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewClosed()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.showWebView)
        assertEquals("fresh-payment", viewModel.uiState.paymentId)
        assertEquals("https://pirate.paycore.pw/pay/fresh", viewModel.uiState.paymentUrl)
        coVerify(exactly = 2) { apiService.createPayment(any(), PayCoreTicker.USDT) }
    }

    @Test
    fun onConfirm_existingPaymentNearExpiration_createsNewPayment() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any(), any()) } returnsMany listOf(
            PayCorePaymentCreateResponse(
                paymentUrl = "https://pirate.paycore.pw/pay/near-expiration",
                uuid = "near-expiration-payment",
                expiresAt = nearExpiresAt(),
            ),
            PayCorePaymentCreateResponse(
                paymentUrl = "https://pirate.paycore.pw/pay/fresh",
                uuid = "fresh-payment",
                expiresAt = futureExpiresAt(),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onWebViewClosed()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.showWebView)
        assertEquals("fresh-payment", viewModel.uiState.paymentId)
        assertEquals("https://pirate.paycore.pw/pay/fresh", viewModel.uiState.paymentUrl)
        coVerify(exactly = 2) { apiService.createPayment(any(), PayCoreTicker.USDT) }
    }

    @Test
    fun init_successCalculatesExactPaymentAmount() = runTest(dispatcher) {
        val requestSlot = slot<PayCorePaymentCalculationRequest>()
        coEvery {
            apiService.calculatePayment(capture(requestSlot), any())
        } returns PayCorePaymentCalculationResponse(
            amountCrypto = BigDecimal("12.5"),
            fullAmountRub = BigDecimal("1000"),
            ticker = "USDT",
            uuid = "calculation-uuid",
            expiresAt = "2026-06-05T00:00:30Z"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(BigDecimal("1000"), viewModel.amountIn)
        assertEquals(BigDecimal("12.5"), viewModel.amountOut)
        assertEquals("calculation-uuid", viewModel.uiState.calculationUuid)
        assertEquals(BigDecimal.ONE, requestSlot.captured.amount)
        assertEquals(PayCoreAmountType.RUB, requestSlot.captured.amountType)
        assertEquals(PayCoreTicker.USDT, requestSlot.captured.ticker)
    }

    private fun givenActiveAccount(id: String = "account-id") {
        val account = mockk<Account>()
        every { account.id } returns id
        every { accountManager.activeAccount } returns account
    }

    private fun givenPaymentCreateResponse(
        paymentUrl: String,
        uuid: String,
        expiresAt: String = futureExpiresAt(),
    ) {
        coEvery { apiService.createPayment(any(), any()) } returns PayCorePaymentCreateResponse(
            paymentUrl = paymentUrl,
            uuid = uuid,
            expiresAt = expiresAt,
        )
    }

    private fun futureExpiresAt(): String {
        return Instant.now().plusSeconds(600).toString()
    }

    private fun nearExpiresAt(): String {
        return Instant.now().plusSeconds(1).toString()
    }

    private fun pastExpiresAt(): String {
        return Instant.now().minusSeconds(1).toString()
    }

    private fun createViewModel() = PayCorePaymentViewModel(
        apiService = apiService,
        walletApprovalService = walletApprovalService,
        storage = storage,
        accountManager = accountManager,
        params = PayCorePaymentParams(
            amountIn = BigDecimal.ONE,
            amountOut = BigDecimal.TEN,
            networkType = PayCoreTicker.USDT,
            tokenInUid = "rub",
            tokenOutUid = "tether",
            blockchainTypeIn = "unsupported",
            blockchainTypeOut = "tron",
            addressOut = "TUserUsdtAddress"
        )
    )
}

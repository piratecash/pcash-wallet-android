package cash.p.terminal.modules.paycore.payment

import cash.p.terminal.R
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCorePaymentCreateResponse
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class PayCorePaymentViewModelTest {

    private val apiService = mockk<PayCoreApiService>()
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true).also {
        every { it.activeAccount } returns null
    }
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onConfirm_successExtractsPaymentIdFromUrl() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any()) } returns PayCorePaymentCreateResponse(
            url = "https://pirate.paycore.pw/payment/payment-123"
        )

        val viewModel = createViewModel()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals("payment-123", viewModel.uiState.paymentId)
        assertEquals("https://pirate.paycore.pw/payment/payment-123", viewModel.uiState.paymentUrl)
    }

    @Test
    fun onConfirm_networkError_showsNoInternetMessage() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any()) } throws IllegalStateException(
            "host.not.found",
            UnknownHostException()
        )

        val viewModel = createViewModel()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals(Translator.getString(R.string.Hud_Text_NoInternet), viewModel.uiState.error)
    }

    @Test
    fun onWebViewCompleted_paymentIdMissing_doesNotSaveTransaction() = runTest(dispatcher) {
        coEvery { apiService.createPayment(any()) } returns PayCorePaymentCreateResponse(
            url = "https://pirate.paycore.pw/pay"
        )

        val viewModel = createViewModel()

        viewModel.onConfirm()
        advanceUntilIdle()

        assertNull(viewModel.uiState.paymentId)

        viewModel.onWebViewCompleted()

        verify(exactly = 0) { storage.save(any()) }
    }

    private fun createViewModel() = PayCorePaymentViewModel(
        apiService = apiService,
        storage = storage,
        accountManager = accountManager,
        params = PayCorePaymentParams(
            amountIn = BigDecimal.ONE,
            amountOut = BigDecimal.TEN,
            networkType = "TRC20",
            tokenInUid = "rub",
            tokenOutUid = "tether",
            blockchainTypeIn = "unsupported",
            blockchainTypeOut = "tron",
            addressOut = "TUserUsdtAddress"
        )
    )
}

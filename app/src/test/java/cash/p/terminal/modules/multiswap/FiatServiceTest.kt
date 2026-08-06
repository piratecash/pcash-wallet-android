package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.paycore.PayCoreAssets
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FiatServiceTest {

    private val assetFiatRateService = mockk<AssetFiatRateService>()
    private val currency = Currency("USD", "$", 2, 0)
    private val token = PayCoreAssets.rubToken

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun setAmount_fiatToken_updatesFiatAmount() = runTest {
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns flowOf(BigDecimal("0.01"))
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setAmount(BigDecimal("100"))

        assertBigDecimalEquals(BigDecimal.ONE, service.stateFlow.value.fiatAmount)
    }

    @Test
    fun setFiatAmount_fiatToken_usesTokenDecimals() = runTest {
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns flowOf(BigDecimal("0.30"))
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setFiatAmount(BigDecimal.ONE)

        assertBigDecimalEquals(BigDecimal("3.33"), service.stateFlow.value.amount)
    }

    @Test
    fun setFiatAmount_zeroRate_keepsAmountNull() = runTest {
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns flowOf(BigDecimal.ZERO)
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setFiatAmount(BigDecimal.ONE)

        assertNull(service.stateFlow.value.amount)
    }

    @Test
    fun setInputAmount_rateChanges_preservesTokenAmount() = runTest {
        val rateFlow = MutableStateFlow<BigDecimal?>(BigDecimal("0.25"))
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns rateFlow
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setInputAmount(BigDecimal("4"))
        rateFlow.value = BigDecimal("0.50")
        advanceUntilIdle()

        assertBigDecimalEquals(BigDecimal("4"), service.stateFlow.value.amount)
        assertBigDecimalEquals(BigDecimal("2"), service.stateFlow.value.fiatAmount)
    }

    @Test
    fun setFiatAmount_rateChanges_preservesFiatAmount() = runTest {
        val rateFlow = MutableStateFlow<BigDecimal?>(BigDecimal("0.25"))
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns rateFlow
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setFiatAmount(BigDecimal.ONE)
        rateFlow.value = BigDecimal("0.20")
        advanceUntilIdle()

        assertBigDecimalEquals(BigDecimal("5"), service.stateFlow.value.amount)
        assertBigDecimalEquals(BigDecimal.ONE, service.stateFlow.value.fiatAmount)
    }

    @Test
    fun setAmount_fiatInputActive_doesNotOverrideConvertedAmount() = runTest {
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns flowOf(BigDecimal("0.25"))
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setFiatAmount(BigDecimal.ONE)
        service.setAmount(BigDecimal("100"))

        assertBigDecimalEquals(BigDecimal("4"), service.stateFlow.value.amount)
        assertBigDecimalEquals(BigDecimal.ONE, service.stateFlow.value.fiatAmount)
    }

    @Test
    fun useTokenAmount_afterFiatInput_usesConvertedTokenAsSource() = runTest {
        val rateFlow = MutableStateFlow<BigDecimal?>(BigDecimal("0.25"))
        every { assetFiatRateService.rateFlow("swap", token, currency) } returns rateFlow
        val service = createService(StandardTestDispatcher(testScheduler))

        service.setCurrency(currency)
        service.setToken(token)
        advanceUntilIdle()
        service.setFiatAmount(BigDecimal.ONE)
        service.useTokenAmount()
        rateFlow.value = BigDecimal("0.50")
        advanceUntilIdle()

        assertBigDecimalEquals(BigDecimal("4"), service.stateFlow.value.amount)
        assertBigDecimalEquals(BigDecimal("2"), service.stateFlow.value.fiatAmount)
    }

    private fun createService(dispatcher: TestDispatcher): FiatService {
        return FiatService(
            assetFiatRateService = assetFiatRateService,
            dispatcher = dispatcher,
        )
    }

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal?) {
        assertEquals(0, expected.compareTo(requireNotNull(actual)))
    }
}

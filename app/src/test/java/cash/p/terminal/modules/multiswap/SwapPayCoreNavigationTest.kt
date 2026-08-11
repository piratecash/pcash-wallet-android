package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.modules.paycore.PayCoreQuote
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SwapPayCoreNavigationTest {

    private val rubToken = PayCoreAssets.rubToken
    private val usdtToken = Token(
        coin = Coin(
            uid = "tether",
            name = "Tether",
            code = "USDT",
            marketCapRank = null,
            coinGeckoId = null,
            image = null,
        ),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        decimals = 6,
    )
    private val provider = mockk<IMultiSwapProvider> {
        every { id } returns "paycore"
    }
    private val nonPayCoreProvider = mockk<IMultiSwapProvider> {
        every { id } returns "provider"
    }

    @Test
    fun buildPayCorePaymentPage_exactOut_preservesRequestedAmountInParams() {
        val targetAmount = BigDecimal("12.5")
        val page = requireNotNull(
            buildPayCorePaymentPage(
                exactOutState(targetAmount),
            ),
        )

        val params = page.toPaymentParams("0xReceiveAddress")

        assertEquals(SwapAmountDirection.Out, params.direction)
        assertEquals(0, targetAmount.compareTo(requireNotNull(params.requestedAmountOut)))
        assertEquals("0xReceiveAddress", params.addressOut)
        assertEquals(PayCoreTicker.USDT_ERC20, params.networkType)
    }

    @Test
    fun buildNextPage_twoStepRoute_opensRouteInfo() {
        assertEquals("SwapRouteInfoPage", buildNextPage(stateWithRoute()).javaClass.simpleName)
    }

    @Test
    fun buildNextPage_oneStepRoute_opensConfirmation() {
        assertEquals(
            "SwapConfirmPage",
            buildNextPage(exactOutState(BigDecimal.ONE, nonPayCoreProvider)).javaClass.simpleName,
        )
    }

    @Test
    fun buildNextPage_payCoreRoute_opensPaymentBeforeRouteInfo() {
        assertEquals(
            "PayCorePaymentPage",
            buildNextPage(exactOutState(BigDecimal.ONE).copy(multiSwapRoute = route())).javaClass.simpleName,
        )
    }

    @Test
    fun isMultiSwapRouteReady_requotingRoute_cannotContinue() {
        val routeState = stateWithRoute()

        assertFalse(isMultiSwapRouteReady(quoting = true, route = routeState.multiSwapRoute))
        assertTrue(isMultiSwapRouteReady(quoting = false, route = routeState.multiSwapRoute))
    }

    private fun stateWithRoute(): SwapUiState = exactOutState(BigDecimal.ONE, nonPayCoreProvider).copy(
        multiSwapRoute = route(),
    )

    private fun route(): MultiSwapRoute {
        val quote = mockk<SwapProviderQuote>(relaxed = true)
        return MultiSwapRoute(
            intermediateCoin = mockk(),
            leg1Quotes = listOf(quote),
            leg2Quotes = listOf(quote),
            commissionReserve = BigDecimal.ZERO,
            selectedLeg1Quote = quote,
            selectedLeg2Quote = quote,
        )
    }

    private fun exactOutState(
        targetAmount: BigDecimal,
        provider: IMultiSwapProvider = this.provider,
    ): SwapUiState {
        val quote = SwapProviderQuote(
            provider = provider,
            swapQuote = PayCoreQuote(
                amountOut = targetAmount,
                priceImpact = null,
                fields = emptyList(),
                tokenIn = rubToken,
                tokenOut = usdtToken,
                amountIn = BigDecimal("1000"),
                serviceFee = BigDecimal.ZERO,
                actionRequired = null,
            ),
            executionMode = SwapExecutionMode.NativeExactOut,
        )
        return SwapUiState(
            amountIn = quote.amountIn,
            displayAmountOut = targetAmount,
            tokenIn = rubToken,
            tokenOut = usdtToken,
            quoting = false,
            quotes = listOf(quote),
            preferredProvider = provider,
            quote = quote,
            error = null,
            availableBalance = null,
            displayBalance = null,
            networkFee = null,
            networkFeeFiatAmount = null,
            feeToken = null,
            feeCoinBalance = null,
            insufficientFeeBalance = false,
            balanceHidden = false,
            warningMessage = null,
            priceImpact = null,
            priceImpactLevel = null,
            priceImpactCaution = null,
            fiatAmountIn = null,
            fiatAmountOut = null,
            fiatPriceImpact = null,
            currency = Currency("RUB", "₽", 2, 0),
            fiatAmountInInputEnabled = false,
            fiatAmountOutInputEnabled = false,
            fiatPriceImpactLevel = null,
            timeout = false,
            multiSwapRoute = null,
            direction = SwapAmountDirection.Out,
            requestedAmountOut = targetAmount,
            amountInMax = null,
            amountOutAccuracy = SwapAmountAccuracy.Exact,
            quoteCautions = emptyList(),
        )
    }
}

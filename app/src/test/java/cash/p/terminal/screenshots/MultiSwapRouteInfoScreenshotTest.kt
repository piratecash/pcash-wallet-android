package cash.p.terminal.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.PriceImpactLevel
import cash.p.terminal.modules.multiswap.exchange.ButtonState
import cash.p.terminal.modules.multiswap.exchange.LegStatus
import cash.p.terminal.modules.multiswap.exchange.LegUiState
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangePresentation
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeScreen
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeUiState
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.coinImageUrl
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.math.BigDecimal
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "w393dp-h1200dp-xxhdpi",
)
class MultiSwapRouteInfoScreenshotTest {
    private val numberFormatter = mockk<IAppNumberFormatter>()

    @Before
    fun setUp() {
        stopKoin()
        every { numberFormatter.formatFiatFull(any(), any()) } answers {
            "${secondArg<String>()}${firstArg<BigDecimal>()}"
        }
        every { numberFormatter.formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
        startKoin {
            modules(module { single { numberFormatter } })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun snapshot_routeInfo_rendersRouteCards() = capture(MultiSwapExchangePresentation.RouteInfo)

    @Test
    fun snapshot_execution_rendersRouteCards() = capture(MultiSwapExchangePresentation.Execution)

    private fun capture(presentation: MultiSwapExchangePresentation) {
        val fileName = when (presentation) {
            MultiSwapExchangePresentation.RouteInfo -> "MultiSwapRouteInfo.png"
            MultiSwapExchangePresentation.Execution -> "MultiSwapRouteExecution.png"
        }
        captureRoboImage(filePath = "build/outputs/roborazzi/$fileName") {
            RouteScreenFixture(presentation)
        }
    }
}

@Composable
private fun RouteScreenFixture(presentation: MultiSwapExchangePresentation) {
    val pirate = fixtureToken("pirate", "PIRATE", BlockchainType.Bitcoin)
    val toncoin = fixtureToken("toncoin", "TONCOIN", BlockchainType.Ton)
    val bnb = fixtureToken("bnb", "BNB", BlockchainType.BinanceSmartChain)
    val currency = Currency("USD", "$", 2, 0)
    val leg1 = LegUiState(
        status = if (presentation == MultiSwapExchangePresentation.Execution) {
            LegStatus.Completed
        } else {
            LegStatus.Pending
        },
        providerName = "ChangeNOW",
        providerIcon = R.drawable.ic_change_now,
        tokenIn = pirate,
        tokenOut = toncoin,
        amountIn = BigDecimal("100"), amountOut = BigDecimal("12.5"),
        fiatAmountIn = BigDecimal("100"), fiatAmountOut = BigDecimal("99"), currency = currency,
        badgeIn = "BTC", badgeOut = "TON", coinIn = "PIRATE", coinOut = "TONCOIN",
        amountInFormatted = "100", amountOutFormatted = "12.5",
        priceImpact = BigDecimal("-1"), priceImpactLevel = PriceImpactLevel.Warning,
        coinIconUrlIn = coinImageUrl(pirate.coin.uid), coinIconUrlOut = coinImageUrl(toncoin.coin.uid),
        riskType = ProviderRiskType.Auto, estimationTime = 797,
    )
    val leg2 = LegUiState(
        status = LegStatus.Pending, providerName = "Uniswap", providerIcon = R.drawable.uniswap,
        tokenIn = toncoin, tokenOut = bnb, amountIn = BigDecimal("12.5"), amountOut = BigDecimal("0.8"),
        fiatAmountIn = BigDecimal("99"), fiatAmountOut = BigDecimal("98"), currency = currency,
        badgeIn = "TON", badgeOut = "BSC", coinIn = "TONCOIN", coinOut = "BNB",
        amountInFormatted = "12.5", amountOutFormatted = "0.8",
        priceImpact = BigDecimal("-1.01"), priceImpactLevel = PriceImpactLevel.Warning,
        coinIconUrlIn = coinImageUrl(toncoin.coin.uid), coinIconUrlOut = coinImageUrl(bnb.coin.uid),
        riskType = ProviderRiskType.Auto, estimationTime = 797,
    )
    ComposeAppTheme(darkTheme = true) {
        MultiSwapExchangeScreen(
            uiState = MultiSwapExchangeUiState(
                leg1 = leg1, leg2 = leg2, buttonState = ButtonState.Enabled,
                showContinueLater = presentation == MultiSwapExchangePresentation.Execution,
                presentation = presentation, routeExplanationTokens = listOf("PIRATE", "TONCOIN", "BNB"),
                leg2ProviderClickable = presentation == MultiSwapExchangePresentation.RouteInfo,
            ),
            timeRemainingProgress = { null }, onSwap = {}, onRefresh = {}, onContinueLater = {},
            onDeleteAndClose = {}, onBack = {}, onClickProvider = {},
        )
    }
}

private fun fixtureToken(uid: String, code: String, blockchainType: BlockchainType) = Token(
    coin = Coin(uid = uid, name = code, code = code),
    blockchain = Blockchain(blockchainType, code, null),
    type = TokenType.Native,
    decimals = 8,
)

package cash.p.terminal.modules.premium

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.domain.usecase.GetLocalizedAssetUseCase
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.markdown.MarkdownBlock
import cash.p.terminal.modules.markdown.MarkdownVisitorBlock
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.commonmark.parser.Parser

class AboutPremiumViewModel(
    private val getLocalizedAssetUseCase: GetLocalizedAssetUseCase,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val currencyManager: CurrencyManager,
    private val numberFormatter: IAppNumberFormatter
) : ViewModel() {
    companion object {
        private const val DEFAULT_PIRATE_ROI = "(~$200, ROI ~8%)"
        private const val DEFAULT_COSA_ROI = "(~$400, ROI ~30%)"
    }
    var uiState by mutableStateOf(AboutPremiumUiState())
        private set

    init {
        loadContent()
    }

    fun retry() {
        uiState = uiState.copy(viewState = ViewState.Loading)
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                val contentDeferred = async {
                    getLocalizedAssetUseCase(GetLocalizedAssetUseCase.ABOUT_PREMIUM_PREFIX)
                }
                val premiumDeferred = async {
                    checkPremiumUseCase.update()
                }
                val roiPirateValueDeferred = async {
                    calculateRoi(
                        coinType = StackingType.PCASH.value,
                        amount = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE
                    ) ?: DEFAULT_PIRATE_ROI
                }
                val roiCosaValueDeferred = async {
                    calculateRoi(
                        coinType = StackingType.COSANTA.value,
                        amount = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA
                    ) ?: DEFAULT_COSA_ROI
                }

                val results = awaitAll(
                    contentDeferred,
                    premiumDeferred,
                    roiPirateValueDeferred,
                    roiCosaValueDeferred
                )

                val content = results[0] as String
                val hasPremium = results[1] as Boolean
                val roiPirateValue = results[2] as String
                val roiCosaValue = results[3] as String

                val processedContent = content
                    .replace("ROI_PIRATE", roiPirateValue)
                    .replace("ROI_COSA", roiCosaValue)

                val markdownBlocks = getMarkdownBlocks(processedContent)

                uiState = uiState.copy(
                    viewState = ViewState.Success,
                    markdownBlocks = markdownBlocks,
                    hasPremium = hasPremium
                )
            } catch (e: Exception) {
                uiState = uiState.copy(viewState = ViewState.Error(e))
            }
        }
    }

    private fun getMarkdownBlocks(content: String): List<MarkdownBlock> {
        val parser = Parser.builder().build()
        val document = parser.parse(content)

        val markdownVisitor = MarkdownVisitorBlock()

        document.accept(markdownVisitor)

        return markdownVisitor.blocks
    }

    private suspend fun calculateRoi(coinType: String, amount: Int): String? {
        return try {
            val calculatorData =
                piratePlaceRepository.getCalculatorData(coinType, amount.toDouble())
            val itemData = calculatorData.items.find {
                it.periodType == PeriodType.YEAR
            } ?: return null

            val baseCurrency = currencyManager.baseCurrency.code.lowercase()
            var baseCurrencySymbol = currencyManager.baseCurrency.symbol
            var earnInYear = itemData.price[baseCurrency]
            if (earnInYear == null && baseCurrency != "usd") {
                baseCurrencySymbol = "$"
                earnInYear = itemData.price["usd"]
            }
            if(earnInYear == null) return null

            val premiumValueInCurrency = ((earnInYear / itemData.amount) * amount).toBigDecimal()
            val formatted = numberFormatter.formatFiatShort(
                value = premiumValueInCurrency,
                symbol = baseCurrencySymbol,
                currencyDecimals = 0
            )

            val roi = numberFormatter.formatNumberShort(
                ((itemData.amount / amount) * 100).toBigDecimal(),
                maximumFractionDigits = 0
            )

            return "($formatted, ROI $roi%)"
        } catch (e: Exception) {
            null
        }
    }
}

data class AboutPremiumUiState(
    val viewState: ViewState = ViewState.Loading,
    val markdownBlocks: List<MarkdownBlock> = emptyList(),
    val hasPremium: Boolean = true,
)

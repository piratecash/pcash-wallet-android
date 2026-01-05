package cash.p.terminal.modules.premium.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.domain.usecase.GetLocalizedAssetUseCase
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.network.pirate.domain.enity.PremiumAccountEligibility
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.eligibleForPremium
import cash.p.terminal.wallet.premiumEligibility
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AboutPremiumViewModel(
    private val getLocalizedAssetUseCase: GetLocalizedAssetUseCase,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val accountManager: IAccountManager,
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

    private val _uiEvents = Channel<TrialPremiumResult>(Channel.UNLIMITED)
    val uiEvents = _uiEvents.receiveAsFlow()

    init {
        loadContent()
    }

    fun activateDemoPremium() {
        viewModelScope.launch {
            uiState = uiState.copy(activationViewState = ViewState.Loading)

            val currentAccount = accountManager.activeAccount
            val premiumAccountEligibility = currentAccount?.premiumEligibility()
            if (premiumAccountEligibility == PremiumAccountEligibility.ELIGIBLE) {
                try {
                    val result = checkPremiumUseCase.activateTrialPremium(currentAccount.id)

                    uiState = uiState.copy(
                        hasPremium = checkPremiumUseCase.getPremiumType().isPremium(),
                        demoDaysLeft = (result as? TrialPremiumResult.DemoActive)?.daysLeft,
                        activationViewState = ViewState.Success
                    )

                    _uiEvents.trySend(result)

                } catch (e: Exception) {
                    val errorResult = TrialPremiumResult.DemoError()

                    uiState = uiState.copy(
                        activationViewState = ViewState.Success
                    )

                    _uiEvents.trySend(errorResult)
                }
            } else {
                val errorResult = TrialPremiumResult.DemoError(premiumAccountEligibility)

                uiState = uiState.copy(
                    activationViewState = ViewState.Success
                )

                _uiEvents.trySend(errorResult)
            }
        }
    }

    fun retry() {
        uiState = uiState.copy(viewState = ViewState.Loading)
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                checkPremiumUseCase.update()
                val isPremium = checkPremiumUseCase.getPremiumType()
                val isTrialPremium = checkPremiumUseCase.isTrialPremium()

                val infoToLoad = if (isTrialPremium) {
                    GetLocalizedAssetUseCase.ABOUT_PREMIUM_PREFIX
                } else {
                    GetLocalizedAssetUseCase.ABOUT_PREMIUM_FULL_PREFIX
                }

                val contentDeferred = async {
                    getLocalizedAssetUseCase(infoToLoad)
                }

                val demoDaysDeferred = async {
                    getDemoDaysLeft()
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
                    demoDaysDeferred,
                    roiPirateValueDeferred,
                    roiCosaValueDeferred
                )

                val content = results[0] as String
                val demoDaysLeft = results[1] as Int?
                val roiPirateValue = results[2] as String
                val roiCosaValue = results[3] as String

                val processedContent = content
                    .replace("ROI_PIRATE", roiPirateValue)
                    .replace("ROI_COSA", roiCosaValue)
                    .run {
                        if (isTrialPremium) {
                            replace("WALLETNAME", accountManager.activeAccount?.name.orEmpty())
                        } else {
                            this
                        }
                    }

                val hasEligibleWallets = hasEligibleWallets()

                uiState = uiState.copy(
                    viewState = ViewState.Success,
                    markdownContent = processedContent,
                    hasPremium = isPremium.isPremium(),
                    demoDaysLeft = demoDaysLeft,
                    hasEligibleWallets = hasEligibleWallets
                )
            } catch (e: Exception) {
                uiState = uiState.copy(viewState = ViewState.Error(e))
            }
        }
    }

    private fun hasEligibleWallets(): Boolean {
        return accountManager.accounts.any { account ->
            account.eligibleForPremium()
        }
    }

    private fun isPremiumCandidateAccount(account: Account): Boolean {
        return account.type is AccountType.Mnemonic && !account.isWatchAccount && account.hasAnyBackup
    }

    private suspend fun getDemoDaysLeft(): Int? {
        val activeAccount = accountManager.activeAccount ?: return null

        if (!isPremiumCandidateAccount(activeAccount)) {
            return null
        }

        return (checkPremiumUseCase.checkTrialPremiumStatus() as? TrialPremiumResult.DemoActive)?.daysLeft
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
            if (earnInYear == null) return null

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
    val markdownContent: String? = null,
    val hasPremium: Boolean = false,
    val demoDaysLeft: Int? = null,
    val hasEligibleWallets: Boolean = false,
    val activationViewState: ViewState = ViewState.Success,
)
package cash.p.terminal.feature.miniapp.ui.miniapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.feature.miniapp.domain.usecase.GetMiniAppBalanceUseCase
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import java.math.BigDecimal

class MiniAppViewModel(
    private val codeStorage: IUniqueCodeStorage,
    private val marketKitWrapper: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val numberFormatter: IAppNumberFormatter,
    private val getMiniAppBalanceUseCase: GetMiniAppBalanceUseCase
) : ViewModel() {

    var uiState by mutableStateOf(MiniAppUiState())
        private set

    fun updateConnectionStatus() {
        val isConnected = codeStorage.uniqueCode.isNotBlank()

        // Show cached balance immediately to avoid UI jumping
        val (cachedFormatted, cachedFiatValue) = calculateRewardData(
            codeStorage.cachedBalance.toBigDecimalOrNull()
        )

        uiState = uiState.copy(
            isConnected = isConnected,
            pirateBalanceText = if (isConnected) cachedFormatted else null,
            bonusFiatValue = if (isConnected) cachedFiatValue else ""
        )

        if (isConnected) fetchBalance()
    }

    private fun fetchBalance() {
        viewModelScope.launch {
            val balance = getMiniAppBalanceUseCase()
            val (formatted, fiatValue) = calculateRewardData(balance)

            // Cache the balance for next time
            balance?.let { codeStorage.cachedBalance = it.toPlainString() }

            uiState = uiState.copy(
                pirateBalanceText = formatted,
                bonusFiatValue = fiatValue
            )
        }
    }

    private fun calculateRewardData(balance: BigDecimal?): Pair<String?, String> {
        val reward = balance?.movePointLeft(1)
        val formatted = reward?.let { formatReward(it) }
        val fiatValue = reward?.let { calculateFiatValue(it) } ?: ""
        return formatted to fiatValue
    }

    private fun formatReward(balance: BigDecimal): String {
        return "+${numberFormatter.formatCoinShort(balance, "PIRATE", 8)}"
    }

    private fun calculateFiatValue(pirateAmount: BigDecimal): String {
        val currency = currencyManager.baseCurrency
        val piratePrice = marketKitWrapper.coinPrice(
            BlockchainType.PirateCash.uid,
            currency.code
        )?.value ?: return ""

        val value = piratePrice * pirateAmount
        return "${numberFormatter.formatFiatShort(value, currency.symbol, 0)}"
    }
}

data class MiniAppUiState(
    val isConnected: Boolean = false,
    val bonusFiatValue: String = "",
    val pirateBalanceText: String? = null
)

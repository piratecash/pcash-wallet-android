package cash.p.terminal.feature.miniapp.ui.miniapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

class MiniAppViewModel(
    private val codeStorage: IUniqueCodeStorage,
    private val marketKitWrapper: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val numberFormatter: IAppNumberFormatter
) : ViewModel() {

    companion object {
        private const val BONUS_PIRATE_AMOUNT = 1000
    }

    var uiState by mutableStateOf(MiniAppUiState())
        private set

    fun updateConnectionStatus() {
        uiState = uiState.copy(
            isConnected = !codeStorage.uniqueCode.isBlank(),
            bonusFiatValue = calculateBonusFiatValue()
        )
    }

    private fun calculateBonusFiatValue(): String {
        val currency = currencyManager.baseCurrency
        val piratePrice = marketKitWrapper.coinPrice(
            BlockchainType.PirateCash.uid,
            currency.code
        )?.value ?: return ""

        val bonusValue = piratePrice * BigDecimal(BONUS_PIRATE_AMOUNT)
        return "+${numberFormatter.formatFiatShort(bonusValue, currency.symbol, 0)}"
    }
}

data class MiniAppUiState(
    val isConnected: Boolean = false,
    val bonusFiatValue: String = ""
)

package cash.p.terminal.modules.amount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.ext.onFirstWith
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.xrate.XRateService

class AmountInputModeViewModel(
    private val localStorage: ILocalStorage,
    xRateService: XRateService,
    coinUid: String
) : ViewModel() {

    private var hasXRate = xRateService.getRate(coinUid) != null

    var inputType by mutableStateOf(
        when {
            hasXRate -> localStorage.amountInputType ?: AmountInputType.COIN
            else -> AmountInputType.COIN
        }
    )
        private set

    init {
        xRateService.getRateFlow(coinUid)
            .onFirstWith(viewModelScope) {
                hasXRate = true
            }
    }

    fun onToggleInputType() {
        if (!hasXRate) return

        inputType = inputType.reversed()
        localStorage.amountInputType = inputType
    }
}


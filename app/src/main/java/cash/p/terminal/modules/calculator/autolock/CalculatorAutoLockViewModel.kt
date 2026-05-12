package cash.p.terminal.modules.calculator.autolock

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.calculator.domain.CalculatorAutoLockOption
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

class CalculatorAutoLockViewModel(
    private val localStorage: ILocalStorage,
) : ViewModelUiState<CalculatorAutoLockUiState>() {

    init {
        viewModelScope.launch {
            localStorage.calculatorAutoLockOptionFlow.collect { emitState() }
        }
    }

    override fun createState() = CalculatorAutoLockUiState(
        selected = localStorage.calculatorAutoLockOption,
    )

    fun onSelect(option: CalculatorAutoLockOption) {
        localStorage.calculatorAutoLockOption = option
    }
}

data class CalculatorAutoLockUiState(
    val selected: CalculatorAutoLockOption,
)

package cash.p.terminal.modules.calculator.lockscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.modules.calculator.domain.CalculatorEvalResult
import cash.p.terminal.modules.calculator.domain.CalculatorExpressionEvaluator
import cash.p.terminal.modules.calculator.domain.CalculatorPinAttemptThrottle
import cash.p.terminal.modules.pin.PinModule
import cash.p.terminal.modules.pin.unlock.AttemptPinUnlockUseCase
import cash.p.terminal.strings.helpers.Translator
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

class CalculatorLockScreenViewModel(
    private val throttle: CalculatorPinAttemptThrottle,
    private val evaluator: CalculatorExpressionEvaluator,
    private val attemptPinUnlock: AttemptPinUnlockUseCase,
    locale: Locale = Locale.getDefault(),
    private val divideByZeroText: String = Translator.getString(R.string.calculator_error_divide_by_zero),
) : ViewModel() {

    private val decimalSeparator: Char =
        DecimalFormatSymbols.getInstance(locale).decimalSeparator

    private var attemptInFlight = false

    var uiState by mutableStateOf(
        CalculatorLockScreenUiState(
            expression = "",
            displayedResult = "0",
            decimalSeparator = decimalSeparator,
            unlocked = false,
        )
    )
        private set

    fun onDigitClick(digit: Char) {
        if (!digit.isDigit()) return
        if (uiState.expression.lastOrNull() == '%') return
        appendToExpression(digit.toString())
    }

    fun onOperatorClick(operator: Char) {
        if (operator !in OPERATORS) return
        if (operator == '%') {
            appendPercent()
            return
        }

        val current = uiState.expression
        if (current.isEmpty()) {
            if (operator != '-') return
            updateExpression(operator.toString())
            return
        }
        val last = current.last()
        if (last in BINARY_OPERATORS) {
            updateExpression(current.dropLast(1) + operator)
        } else {
            appendToExpression(operator.toString())
        }
    }

    private fun appendPercent() {
        val current = uiState.expression
        val last = current.lastOrNull() ?: return
        if (last == '%') return
        if (!last.isDigit() && last != ')') return
        appendToExpression("%")
    }

    fun onDecimalClick() {
        val current = uiState.expression
        if (current.lastOrNull() == '%') return
        val lastNumber = current.takeLastWhile { it.isDigit() || it == decimalSeparator }
        if (lastNumber.contains(decimalSeparator)) return
        if (lastNumber.isEmpty()) {
            appendToExpression("0$decimalSeparator")
        } else {
            appendToExpression(decimalSeparator.toString())
        }
    }

    fun onOpenParenClick() {
        appendToExpression("(")
    }

    fun onCloseParenClick() {
        val current = uiState.expression
        val open = current.count { it == '(' }
        val close = current.count { it == ')' }
        if (open <= close) return
        if (current.isEmpty() || current.last() in BINARY_OPERATORS || current.last() == '(') return
        appendToExpression(")")
    }

    fun onParenClick() {
        if (canClose()) onCloseParenClick() else onOpenParenClick()
    }

    fun onToggleSignClick() {
        val current = uiState.expression
        if (current.isEmpty()) return

        val numberStart = lastNumberStart(current)
        if (numberStart == current.length) {
            stripTrailingUnaryMinus(current)
            return
        }
        toggleSignBeforeNumber(current, numberStart)
    }

    private fun canClose(): Boolean {
        val current = uiState.expression
        val open = current.count { it == '(' }
        val close = current.count { it == ')' }
        if (open <= close) return false
        val last = current.lastOrNull() ?: return false
        return last !in BINARY_OPERATORS && last != '('
    }

    private fun lastNumberStart(expression: String): Int {
        var i = expression.length
        while (i > 0 && (expression[i - 1].isDigit() || expression[i - 1] == decimalSeparator)) {
            i--
        }
        return i
    }

    private fun stripTrailingUnaryMinus(expression: String) {
        if (expression.last() != '-') return
        val before = expression.getOrNull(expression.length - 2)
        if (before == null || before == '(' || before in BINARY_OPERATORS) {
            updateExpression(expression.dropLast(1))
        }
    }

    private fun toggleSignBeforeNumber(expression: String, numberStart: Int) {
        if (numberStart == 0) {
            updateExpression("-$expression")
            return
        }
        val prev = expression[numberStart - 1]
        val beforePrev = expression.getOrNull(numberStart - 2)
        val prevIsUnary = beforePrev == null || beforePrev == '(' || beforePrev in BINARY_OPERATORS
        val prefix = expression.substring(0, numberStart - 1)
        val suffix = expression.substring(numberStart)
        val updated = when {
            prev == '-' && prevIsUnary -> prefix + suffix
            prev == '-' -> "$prefix+$suffix"
            prev == '+' -> "$prefix-$suffix"
            prev == '(' || prev in BINARY_OPERATORS ->
                expression.substring(0, numberStart) + '-' + suffix
            else -> return
        }
        updateExpression(updated)
    }

    fun onDeleteClick() {
        val current = uiState.expression
        if (current.isEmpty()) return
        updateExpression(current.dropLast(1))
    }

    fun onClearClick() {
        uiState = uiState.copy(
            expression = "",
            displayedResult = "0",
        )
    }

    fun onEqualsClick() {
        if (attemptInFlight) return
        val expression = uiState.expression
        if (expression.isEmpty()) return

        when (val result = evaluator.evaluate(expression, decimalSeparator)) {
            is CalculatorEvalResult.DivideByZero -> showDivideByZero()
            is CalculatorEvalResult.Invalid -> return
            is CalculatorEvalResult.Success -> handleEvaluatedValue(result.value)
        }
    }

    fun onUnlockedConsumed() {
        uiState = uiState.copy(
            unlocked = false,
            expression = "",
            displayedResult = "0",
        )
    }

    private fun handleEvaluatedValue(value: BigDecimal) {
        uiState = uiState.copy(displayedResult = formatForDisplay(value))

        val candidatePin = value.toPinCandidate() ?: return
        if (!throttle.tryConsume()) return

        attemptInFlight = true
        viewModelScope.launch {
            try {
                if (attemptPinUnlock(candidatePin)) {
                    throttle.reset()
                    uiState = uiState.copy(unlocked = true)
                }
            } finally {
                attemptInFlight = false
            }
        }
    }

    private fun appendToExpression(text: String) {
        updateExpression(uiState.expression + text)
    }

    private fun updateExpression(newExpression: String) {
        val live = liveResultOrNull(newExpression)
        uiState = uiState.copy(
            expression = newExpression,
            displayedResult = live ?: uiState.displayedResult,
        )
    }

    private fun liveResultOrNull(expression: String): String? {
        if (expression.isEmpty()) return "0"
        if (expression.last() in BINARY_OPERATORS) return null
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        if (open != close) return null
        return when (val result = evaluator.evaluate(expression, decimalSeparator)) {
            is CalculatorEvalResult.DivideByZero -> divideByZeroText
            is CalculatorEvalResult.Invalid -> null
            is CalculatorEvalResult.Success -> formatForDisplay(result.value)
        }
    }

    private fun showDivideByZero() {
        uiState = uiState.copy(displayedResult = divideByZeroText)
    }

    private fun formatForDisplay(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val plain = if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
        return if (decimalSeparator == '.') plain else plain.replace('.', decimalSeparator)
    }

    private fun BigDecimal.toPinCandidate(): String? {
        if (signum() < 0) return null
        val stripped = stripTrailingZeros()
        if (stripped.scale() > 0) return null
        val digits = stripped.toBigInteger().toString()
        if (digits.length > PinModule.PIN_COUNT) return null
        return digits.padStart(PinModule.PIN_COUNT, '0')
    }

    companion object {
        private val BINARY_OPERATORS = setOf('+', '-', '×', '÷')
        private val OPERATORS = BINARY_OPERATORS + '%'
    }
}

data class CalculatorLockScreenUiState(
    val expression: String,
    val displayedResult: String,
    val decimalSeparator: Char,
    val unlocked: Boolean,
)

package cash.p.terminal.modules.calculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorExpressionEvaluatorTest {

    private val evaluator = CalculatorExpressionEvaluator()

    @Test
    fun evaluate_singleNumber_returnsValue() {
        assertSuccess("1234", BigDecimal("1234"))
    }

    @Test
    fun evaluate_simpleAddition_returnsSum() {
        assertSuccess("2+3", BigDecimal("5"))
    }

    @Test
    fun evaluate_simpleSubtraction_returnsDifference() {
        assertSuccess("10-7", BigDecimal("3"))
    }

    @Test
    fun evaluate_multiplicationPrecedenceOverAddition_resolvesCorrectly() {
        assertSuccess("2+3*4", BigDecimal("14"))
    }

    @Test
    fun evaluate_divisionPrecedenceOverSubtraction_resolvesCorrectly() {
        assertSuccess("20-4/2", BigDecimal("18"))
    }

    @Test
    fun evaluate_parenthesesOverridePrecedence_resolvesCorrectly() {
        assertSuccess("(2+3)*4", BigDecimal("20"))
    }

    @Test
    fun evaluate_unicodeOperators_resolvesSameAsAscii() {
        assertSuccess("6×7", BigDecimal("42"))
        assertSuccess("100÷4", BigDecimal("25"))
        assertSuccess("9−4", BigDecimal("5"))
    }

    @Test
    fun evaluate_unaryMinus_returnsNegative() {
        assertSuccess("-5", BigDecimal("-5"))
    }

    @Test
    fun evaluate_doubleUnaryMinus_returnsPositive() {
        assertSuccess("--7", BigDecimal("7"))
    }

    @Test
    fun evaluate_unaryPlus_returnsPositive() {
        assertSuccess("+9", BigDecimal("9"))
    }

    @Test
    fun evaluate_decimalPointSeparator_parses() {
        assertSuccess("1.5+2.5", BigDecimal("4.0"))
    }

    @Test
    fun evaluate_commaSeparator_parsesWhenLocaleMatches() {
        val result = evaluator.evaluate("1,5+2,5", decimalSeparator = ',')
        assertEquals(BigDecimal("4.0"), (result as CalculatorEvalResult.Success).value)
    }

    @Test
    fun evaluate_standalonePercent_returnsFraction() {
        assertSuccess("10%", BigDecimal("0.1"))
    }

    @Test
    fun evaluate_subtractPercent_returnsPercentOfLeftSubtracted() {
        assertSuccess("10-10%", BigDecimal("9"))
    }

    @Test
    fun evaluate_addPercent_returnsPercentOfLeftAdded() {
        assertSuccess("10+10%", BigDecimal("11"))
    }

    @Test
    fun evaluate_multiplyByPercent_returnsFractionMultiplier() {
        assertSuccess("10*10%", BigDecimal("1"))
    }

    @Test
    fun evaluate_divideByPercent_returnsFractionDivisor() {
        assertSuccess("10/10%", BigDecimal("100"))
    }

    @Test
    fun evaluate_chainedPercentOperations_usesCurrentLeftValue() {
        assertSuccess("100+10%+10%", BigDecimal("121"))
    }

    @Test
    fun evaluate_legacyModuloSyntax_returnsInvalid() {
        assertInvalid("10%3")
    }

    @Test
    fun evaluate_percentWithoutLeftOperand_returnsInvalid() {
        assertInvalid("%")
        assertInvalid("2+%")
    }

    @Test
    fun evaluate_divisionByZero_returnsDivideByZero() {
        assertDivideByZero("5/0")
    }

    @Test
    fun evaluate_divisionByZeroAfterPercent_returnsDivideByZero() {
        assertDivideByZero("5/0%")
    }

    @Test
    fun evaluate_emptyString_returnsInvalid() {
        assertInvalid("")
    }

    @Test
    fun evaluate_whitespaceOnly_returnsInvalid() {
        assertInvalid("   ")
    }

    @Test
    fun evaluate_trailingOperator_returnsInvalid() {
        assertInvalid("2+")
    }

    @Test
    fun evaluate_unbalancedParens_returnsInvalid() {
        assertInvalid("(2+3")
    }

    @Test
    fun evaluate_unmatchedClosingParen_returnsInvalid() {
        assertInvalid("2+3)")
    }

    @Test
    fun evaluate_letters_returnsInvalid() {
        assertInvalid("2+a")
    }

    @Test
    fun evaluate_consecutiveDecimalPoints_returnsInvalid() {
        assertInvalid("1.2.3")
    }

    @Test
    fun evaluate_pinResultMatchesInteger_returnsExactValue() {
        assertSuccess("12+34", BigDecimal("46"))
        assertSuccess("100/4-12", BigDecimal("13"))
        assertSuccess("(2+3)*(8-3)", BigDecimal("25"))
    }

    @Test
    fun evaluate_whitespaceBetweenTokens_isIgnored() {
        assertSuccess(" 2 + 3 ", BigDecimal("5"))
    }

    @Test
    fun evaluate_additionWithUlpFractional_keepsExactNonIntegerResult() {
        // Bug guard: prior MathContext(32) on add() rounded a 33-digit fractional
        // contribution to zero, letting "123456.000…001 + 0" pass as PIN 123456.
        val expression = "123456.00000000000000000000000000000001+0"
        val result = evaluator.evaluate(expression)
        assertTrue(result is CalculatorEvalResult.Success)
        val value = (result as CalculatorEvalResult.Success).value
        // Exact arithmetic: result must NOT compare equal to integer 123456.
        assertTrue(
            "Expected non-integer fractional result, got ${value.toPlainString()}",
            value.compareTo(BigDecimal("123456")) != 0
        )
        assertTrue(value.scale() > 0)
    }

    @Test
    fun evaluate_subtractionWithUlpFractional_keepsExactNonIntegerResult() {
        val expression = "123457-0.99999999999999999999999999999999"
        val result = evaluator.evaluate(expression)
        assertTrue(result is CalculatorEvalResult.Success)
        val value = (result as CalculatorEvalResult.Success).value
        assertTrue(value.compareTo(BigDecimal("123456")) != 0)
    }

    @Test
    fun evaluate_multiplicationWithExtraScale_isExact() {
        // 0.1 * 0.1 = 0.01 exactly; no truncation by MathContext.
        assertSuccess("0.1*0.1", BigDecimal("0.01"))
    }

    @Test
    fun evaluate_simpleDecimalAddition_returnsExactSum() {
        // Bug guard: not rounded to 0.
        val result = evaluator.evaluate("0.1+0.2")
        assertTrue(result is CalculatorEvalResult.Success)
        assertEquals(0, (result as CalculatorEvalResult.Success).value.compareTo(BigDecimal("0.3")))
    }

    private fun assertSuccess(expression: String, expected: BigDecimal) {
        val result = evaluator.evaluate(expression)
        assertTrue(
            "Expected Success but got $result for '$expression'",
            result is CalculatorEvalResult.Success
        )
        assertEquals(0, (result as CalculatorEvalResult.Success).value.compareTo(expected))
    }

    private fun assertInvalid(expression: String) {
        val result = evaluator.evaluate(expression)
        assertTrue(
            "Expected Invalid but got $result for '$expression'",
            result is CalculatorEvalResult.Invalid
        )
    }

    private fun assertDivideByZero(expression: String) {
        val result = evaluator.evaluate(expression)
        assertTrue(
            "Expected DivideByZero but got $result for '$expression'",
            result is CalculatorEvalResult.DivideByZero
        )
    }
}

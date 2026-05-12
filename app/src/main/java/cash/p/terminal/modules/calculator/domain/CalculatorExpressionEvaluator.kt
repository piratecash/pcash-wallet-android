package cash.p.terminal.modules.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed class CalculatorEvalResult {
    data class Success(val value: BigDecimal) : CalculatorEvalResult()
    data object DivideByZero : CalculatorEvalResult()
    data object Invalid : CalculatorEvalResult()
}

class CalculatorExpressionEvaluator(
    private val mathContext: MathContext = MathContext(32, RoundingMode.HALF_UP),
) {

    fun evaluate(expression: String, decimalSeparator: Char = '.'): CalculatorEvalResult {
        val tokens = tokenize(expression, decimalSeparator) ?: return CalculatorEvalResult.Invalid
        if (tokens.isEmpty()) return CalculatorEvalResult.Invalid

        val parser = Parser(tokens, mathContext)
        val value = try {
            parser.parseExpression()
        } catch (_: DivisionByZeroException) {
            return CalculatorEvalResult.DivideByZero
        } catch (_: ArithmeticException) {
            return CalculatorEvalResult.Invalid
        } catch (_: ParseException) {
            return CalculatorEvalResult.Invalid
        }
        if (!parser.atEnd()) return CalculatorEvalResult.Invalid
        return CalculatorEvalResult.Success(value.value)
    }

    private fun tokenize(input: String, decimalSeparator: Char): List<Token>? {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < input.length) {
            val ch = input[index]
            index = when {
                ch.isWhitespace() -> index + 1
                ch.isDigit() || ch == decimalSeparator -> {
                    val (number, newIndex) = consumeNumber(input, index, decimalSeparator)
                        ?: return null
                    tokens.add(Token.Number(number))
                    newIndex
                }
                else -> {
                    val operator = OperatorChar.from(ch) ?: return null
                    tokens.add(Token.Operator(operator))
                    index + 1
                }
            }
        }
        return tokens
    }

    private fun consumeNumber(
        input: String,
        start: Int,
        decimalSeparator: Char,
    ): Pair<BigDecimal, Int>? {
        var index = start
        var sawDot = input[index] == decimalSeparator
        index++
        while (index < input.length) {
            val next = input[index]
            val isDecimalSeparator = next == decimalSeparator && !sawDot
            if (next.isDigit()) {
                index++
            } else if (isDecimalSeparator) {
                sawDot = true
                index++
            } else {
                break
            }
        }
        val raw = input.substring(start, index).replace(decimalSeparator, '.')
        val value = raw.toBigDecimalOrNull() ?: return null
        return value to index
    }

    private sealed class Token {
        data class Number(val value: BigDecimal) : Token()
        data class Operator(val op: OperatorChar) : Token()
    }

    private enum class OperatorChar {
        Plus,
        Minus,
        Multiply,
        Divide,
        Percent,
        OpenParen,
        CloseParen,
        ;

        companion object {
            fun from(ch: Char): OperatorChar? = when (ch) {
                '+' -> Plus
                '-', '−' -> Minus
                '*', '×' -> Multiply
                '/', '÷' -> Divide
                '%' -> Percent
                '(' -> OpenParen
                ')' -> CloseParen
                else -> null
            }
        }
    }

    private class ParseException : RuntimeException()
    private class DivisionByZeroException : ArithmeticException()

    private data class ParsedValue(
        val value: BigDecimal,
        val isPercent: Boolean = false,
    )

    private class Parser(
        private val tokens: List<Token>,
        private val mathContext: MathContext,
    ) {
        private var position = 0

        fun atEnd(): Boolean = position >= tokens.size

        fun parseExpression(): ParsedValue {
            var left = parseTerm()
            while (true) {
                val op = peekAdditiveOperator() ?: break
                position++
                val right = parseTerm()
                val rightValue = if (right.isPercent) {
                    left.value.multiply(right.value)
                } else {
                    right.value
                }
                left = ParsedValue(
                    if (op == OperatorChar.Plus) {
                        left.value.add(rightValue)
                    } else {
                        left.value.subtract(rightValue)
                    }
                )
            }
            return left
        }

        private fun parseTerm(): ParsedValue {
            var left = parseFactor()
            while (true) {
                val op = peekMultiplicativeOperator() ?: break
                position++
                left = ParsedValue(applyMultiplicativeOperator(left.value, op, parseFactor().value))
            }
            return left
        }

        private fun applyMultiplicativeOperator(
            left: BigDecimal,
            op: OperatorChar,
            right: BigDecimal,
        ): BigDecimal = when (op) {
            OperatorChar.Multiply -> left.multiply(right)
            OperatorChar.Divide -> divideOrThrow(left, right)
            else -> throw ParseException()
        }

        private fun divideOrThrow(left: BigDecimal, right: BigDecimal): BigDecimal {
            if (right.signum() == 0) throw DivisionByZeroException()
            return left.divide(right, mathContext)
        }

        private fun parseFactor(): ParsedValue {
            val op = peekOperator()
            if (op == OperatorChar.Plus) {
                position++
                return parseFactor()
            }
            if (op == OperatorChar.Minus) {
                position++
                return parseFactor().negate()
            }
            return parsePostfixPercent(parsePrimary())
        }

        private fun ParsedValue.negate(): ParsedValue =
            copy(value = value.negate())

        private fun parsePostfixPercent(value: ParsedValue): ParsedValue {
            var result = value
            while (peekOperator() == OperatorChar.Percent) {
                position++
                result = ParsedValue(
                    value = result.value.movePointLeft(2),
                    isPercent = true,
                )
            }
            return result
        }

        private fun parsePrimary(): ParsedValue {
            val token = tokens.getOrNull(position) ?: throw ParseException()
            return when {
                token is Token.Number -> {
                    position++
                    ParsedValue(token.value)
                }
                token is Token.Operator && token.op == OperatorChar.OpenParen ->
                    parseParenthesized()
                else -> throw ParseException()
            }
        }

        private fun parseParenthesized(): ParsedValue {
            position++
            val value = parseExpression()
            val close = tokens.getOrNull(position) ?: throw ParseException()
            if (close !is Token.Operator || close.op != OperatorChar.CloseParen) {
                throw ParseException()
            }
            position++
            return value
        }

        private fun peekOperator(): OperatorChar? {
            val token = tokens.getOrNull(position) ?: return null
            return (token as? Token.Operator)?.op
        }

        private fun peekAdditiveOperator(): OperatorChar? =
            peekOperator()?.takeIf { it == OperatorChar.Plus || it == OperatorChar.Minus }

        private fun peekMultiplicativeOperator(): OperatorChar? =
            peekOperator()?.takeIf {
                it == OperatorChar.Multiply ||
                    it == OperatorChar.Divide
            }
    }
}

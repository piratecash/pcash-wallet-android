package cash.p.terminal.core

import java.math.BigDecimal
import java.math.BigInteger

fun BigDecimal?.moreThanZero() = this?.compareTo(BigDecimal.ZERO) == 1

fun BigInteger?.moreThanZero() = this?.compareTo(BigInteger.ZERO) == 1

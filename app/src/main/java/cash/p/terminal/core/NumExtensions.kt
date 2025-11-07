package cash.p.terminal.core

import java.math.BigDecimal

fun BigDecimal?.moreThanZero() = this?.compareTo(BigDecimal.ZERO) == 1

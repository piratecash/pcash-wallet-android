package cash.p.terminal.modules.send

import cash.p.terminal.core.isNative
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

internal fun hasInsufficientFeeTokenBalance(
    token: Token,
    fee: BigDecimal?,
    feeTokenBalance: BigDecimal?,
): Boolean {
    if (token.type.isNative) return false
    val currentFee = fee ?: return false
    return currentFee > (feeTokenBalance ?: BigDecimal.ZERO)
}

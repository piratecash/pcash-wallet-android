package cash.p.terminal.modules.paycore

import java.math.BigDecimal

object PayCoreFees {
    private val FEES: Map<String, BigDecimal> = mapOf(
        PayCoreNetworkType.TRC20 to BigDecimal("3"),
        PayCoreNetworkType.BEP20 to BigDecimal("2"),
        PayCoreNetworkType.ERC20 to BigDecimal("2"),
    )

    fun forNetwork(networkType: String): BigDecimal =
        FEES[networkType] ?: BigDecimal.ZERO
}

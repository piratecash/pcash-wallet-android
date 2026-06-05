package cash.p.terminal.modules.paycore

import java.math.BigDecimal

object PayCoreFees {
    private val FEES: Map<String, BigDecimal> = mapOf(
        PayCoreNetworkType.TRC20 to BigDecimal("2"),
        PayCoreNetworkType.ERC20 to BigDecimal("2"),
        PayCoreNetworkType.SPL to BigDecimal("1"),
    )

    fun forNetwork(networkType: String): BigDecimal =
        PayCoreNetworkMapper.normalizeNetworkType(networkType)?.let(FEES::get) ?: BigDecimal.ZERO
}

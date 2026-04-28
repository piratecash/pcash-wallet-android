package cash.p.terminal.network.pirate.domain.enity

import java.time.Instant

data class InvestmentData(
    val balance: String,
    val unrealizedValue: String,
    val mint: String,
    val stakingActive: Boolean,
    val stakingInactiveReason: String? = null,
    val nextAccrualAt: Instant? = null,
    val nextPayoutAt: Instant? = null,
)

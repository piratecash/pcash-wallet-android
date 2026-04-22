package cash.p.terminal.network.pirate.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class InvestmentDataDto(
    val balance: String,
    @SerialName("unrealized_value")
    val unrealizedValue: String,
    val mint: String,
    @SerialName("staking_active")
    val stakingActive: Boolean,
    @SerialName("staking_inactive_reason")
    val stakingInactiveReason: String? = null,
    @SerialName("next_accrual_at")
    val nextAccrualAt: String? = null,
    @SerialName("next_payout_at")
    val nextPayoutAt: String? = null,
)

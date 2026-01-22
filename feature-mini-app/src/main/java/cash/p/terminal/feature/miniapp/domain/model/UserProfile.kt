package cash.p.terminal.feature.miniapp.domain.model

import java.math.BigDecimal

data class UserProfile(
    val balance: BigDecimal,
    val isPremium: Boolean,
    val energy: Int?,
    val maxBalance: BigDecimal?,
    val totalIncome: BigDecimal?,
    val username: String?,
    val firstName: String?,
    val lastName: String?
)

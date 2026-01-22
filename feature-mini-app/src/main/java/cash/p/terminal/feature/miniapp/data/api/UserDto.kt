package cash.p.terminal.feature.miniapp.data.api

import cash.p.terminal.feature.miniapp.domain.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Response from GET /miniapp/users endpoint
 */
@Serializable
data class ProfileResponseDto(
    val balance: String,
    @SerialName("isPremium")
    val isPremium: Boolean,
    val energy: Int? = null,
    @SerialName("maxBalance")
    val maxBalance: String? = null,
    @SerialName("totalIncome")
    val totalIncome: String? = null,
    val username: String? = null,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null
)

fun ProfileResponseDto.toDomain() = UserProfile(
    balance = balance.toBigDecimalOrNull() ?: BigDecimal.ZERO,
    isPremium = isPremium,
    energy = energy,
    maxBalance = maxBalance?.toBigDecimalOrNull(),
    totalIncome = totalIncome?.toBigDecimalOrNull(),
    username = username,
    firstName = firstName,
    lastName = lastName
)

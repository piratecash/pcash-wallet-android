package cash.p.terminal.network.alphaaml.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlphaAmlResponse(
    @SerialName("address") val address: String,
    @SerialName("riskGrade") val riskGrade: String,
    @SerialName("status") val status: String
)

enum class AlphaAmlRiskGrade {
    VeryLow,
    Low,
    High,
    VeryHigh;

    companion object {
        fun fromString(grade: String): AlphaAmlRiskGrade? {
            return when (grade.lowercase()) {
                "very-low" -> VeryLow
                "low" -> Low
                "high" -> High
                "very-high" -> VeryHigh
                else -> null
            }
        }
    }
}

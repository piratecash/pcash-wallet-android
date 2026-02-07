package cash.p.terminal.feature.miniapp.domain.model

import java.math.BigDecimal

/**
 * Data for Step 5 "Special Proposal" screen.
 * Contains calculated values for premium benefits comparison.
 */
data class SpecialProposalData(
    // Guaranteed bonus (calculated from API balance)
    val guaranteedBonus: Int,           // floor(balance * 0.1), min 1
    val guaranteedBonusFiat: String,    // Fiat equivalent

    // PIRATE tab data
    val pirateNotEnough: String,        // Formatted, e.g., "10,000 PIRATE"
    val pirateNotEnoughFiat: String,
    val pirateRoi: String,              // e.g., "7.5%"
    val pirateMonthlyIncome: String,    // e.g., "+56.873 PIRATE"
    val pirateMonthlyIncomeFiat: String, // e.g., "+$1.1888"

    // COSA tab data
    val cosaNotEnough: String,          // Formatted, e.g., "100 COSA"
    val cosaNotEnoughFiat: String,
    val cosaRoi: String,                // e.g., "23.5%"
    val cosaMonthlyIncome: String,      // e.g., "+2.1255 COSA"
    val cosaMonthlyIncomeFiat: String,   // e.g., "+$2.9237"

    // Tab selection
    val cheaperOption: CoinType,        // Which tab requires less USD to reach premium

    // Premium status
    val hasPiratePremium: Boolean,      // pirateBalance >= 10000
    val hasCosaPremium: Boolean,        // cosaBalance >= 100
    val hasPremium: Boolean             // either premium active
)

/**
 * Coin type for tab selection
 */
enum class CoinType {
    PIRATE,
    COSA
}

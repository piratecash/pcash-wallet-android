package cash.p.terminal.modules.transactions

import cash.p.terminal.R

enum class IncomingAddressCheckResult {
    Unknown,
    Low,
    Medium,
    High;

    val title: Int
        get() = when (this) {
            Unknown -> R.string.aml_unknown
            Low -> R.string.aml_low_risk
            Medium -> R.string.aml_medium_risk
            High -> R.string.aml_high_risk
        }
}

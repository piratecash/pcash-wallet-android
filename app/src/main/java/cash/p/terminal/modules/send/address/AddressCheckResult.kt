package cash.p.terminal.modules.send.address

import cash.p.terminal.R

enum class AddressCheckResult {
    Clear,
    Detected,
    NotAvailable,
    NotAllowed,
    NotSupported,
    AlphaAmlVeryLow,
    AlphaAmlLow,
    AlphaAmlHigh,
    AlphaAmlVeryHigh;

    val title: Int
        get() = when (this) {
            Clear -> R.string.Send_Address_Error_Clear
            Detected -> R.string.Send_Address_Error_Detected
            AlphaAmlVeryLow -> R.string.alpha_aml_very_low_risk
            AlphaAmlLow -> R.string.alpha_aml_low_risk
            AlphaAmlHigh -> R.string.alpha_aml_high_risk
            AlphaAmlVeryHigh -> R.string.alpha_aml_very_high_risk
            NotAvailable,
            NotAllowed,
            NotSupported -> R.string.NotAvailable
        }
}

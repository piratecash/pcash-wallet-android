package cash.p.terminal.core.address

import cash.p.terminal.R

enum class AddressCheckType {
    SmartContract,
    Phishing,
    Blacklist,
    Sanction;

    val title: Int
        get() = when (this) {
            SmartContract -> R.string.Send_Address_NotSmartContractCheck
            Phishing -> R.string.Send_Address_PhishingCheck
            Blacklist -> R.string.Send_Address_BlacklistCheck
            Sanction -> R.string.Send_Address_SanctionCheck
        }

    val detectedErrorTitle: Int
        get() = when (this) {
            SmartContract -> R.string.Send_Address_ErrorMessage_SmartContractDetected
            Phishing -> R.string.Send_Address_ErrorMessage_PhishingDetected
            Blacklist -> R.string.Send_Address_ErrorMessage_BlacklistDetected
            Sanction -> R.string.Send_Address_ErrorMessage_SanctionDetected
        }

    val detectedErrorDescription: Int
        get() = when (this) {
            SmartContract -> R.string.Send_Address_ErrorMessage_SmartContractDetected_Description
            Phishing -> R.string.Send_Address_ErrorMessage_PhishingDetected_Description
            Blacklist -> R.string.Send_Address_ErrorMessage_BlacklistDetected_Description
            Sanction -> R.string.Send_Address_ErrorMessage_SanctionDetected_Description
        }

    fun isPremiumRequired(): Boolean {
        return this == SmartContract
    }

}

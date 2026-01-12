package cash.p.terminal.premium.domain

interface PremiumSettings {
    fun getAmlCheckReceivedEnabled(): Boolean
    fun setAmlCheckReceivedEnabled(enabled: Boolean)
    fun getAmlCheckShowAlert(): Boolean
    fun setAmlCheckShowAlert(show: Boolean)
}

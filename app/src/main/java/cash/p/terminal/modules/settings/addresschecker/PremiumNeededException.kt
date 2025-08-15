package cash.p.terminal.modules.settings.addresschecker

internal class PremiumNeededException: Exception("Premium needed for this action") {
    override val message: String?
        get() = "Premium needed for this action. Please upgrade to premium to continue."
}
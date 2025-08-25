package cash.p.terminal.network.pirate.domain.enity

sealed interface TrialPremiumResult {
    data object NeedPremium : TrialPremiumResult
    data class DemoActive(val daysLeft: Int) : TrialPremiumResult
    data object DemoExpired : TrialPremiumResult
    data object DemoNotFound : TrialPremiumResult
    data object InvalidAddress : TrialPremiumResult
    data class DemoError(val premiumAccountEligibility: PremiumAccountEligibility? = null) : TrialPremiumResult
}
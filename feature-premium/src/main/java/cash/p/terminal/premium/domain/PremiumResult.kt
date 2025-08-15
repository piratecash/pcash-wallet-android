package cash.p.terminal.premium.domain

sealed interface PremiumResult {
    data object Success : PremiumResult
    data object NeedPremium : PremiumResult
}
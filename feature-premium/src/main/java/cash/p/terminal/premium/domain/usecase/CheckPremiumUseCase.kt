package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult

interface CheckPremiumUseCase {
    fun isAnyPremium(): Boolean
    fun isTrialPremium(): Boolean
    suspend fun update(): Boolean

    suspend fun checkTrialPremiumStatus(): TrialPremiumResult
    suspend fun activateTrialPremium(accountId: String): TrialPremiumResult
}
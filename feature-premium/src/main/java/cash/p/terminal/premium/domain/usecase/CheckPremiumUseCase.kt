package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult

interface CheckPremiumUseCase {
    fun isPremium(): Boolean
    fun startAccountMonitorUpdate()
    suspend fun update(): Boolean

    suspend fun checkTrialPremiumStatus(): TrialPremiumResult
    suspend fun activateTrialPremium(accountId: String): TrialPremiumResult
}
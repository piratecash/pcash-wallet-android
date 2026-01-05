package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult

interface CheckPremiumUseCase {
    fun getPremiumType(): PremiumType
    fun getParentPremiumType(userLevel: Int): PremiumType
    suspend fun isPremiumWithParentInCache(userLevel: Int): Boolean

    fun isTrialPremium(): Boolean
    suspend fun update(): PremiumType

    suspend fun checkTrialPremiumStatus(): TrialPremiumResult
    suspend fun activateTrialPremium(accountId: String): TrialPremiumResult
}

enum class PremiumType {
    NONE, TRIAL, COSA, PIRATE;

    fun isPremium(): Boolean {
        return this != NONE
    }
}
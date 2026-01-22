package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.wallet.Account

interface CheckPremiumUseCase {
    fun getPremiumType(): PremiumType
    fun getParentPremiumType(userLevel: Int): PremiumType
    suspend fun isPremiumWithParentInCache(userLevel: Int): Boolean

    fun isTrialPremium(): Boolean
    suspend fun update(): PremiumType

    suspend fun checkTrialPremiumStatus(): TrialPremiumResult
    suspend fun activateTrialPremium(accountId: String): TrialPremiumResult

    /**
     * Checks premium status by querying blockchain balance directly.
     * Always performs a fresh balance check.
     */
    suspend fun checkPremiumByBalanceForAccount(account: Account, checkTrial: Boolean = true): PremiumType
}

enum class PremiumType {
    NONE, TRIAL, COSA, PIRATE;

    fun isPremium(): Boolean {
        return this != NONE
    }
}
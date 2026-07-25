package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.wallet.Account
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * Observable per-account premium type (keyed by account id), for the wallet-list screens. Seeded from
     * the persisted cache on startup so a cold start shows the last known type immediately, then updated in
     * the background as the re-scan completes. An active trial takes display precedence over the token type;
     * accounts absent from the map are treated as [PremiumType.NONE].
     */
    val premiumTypesFlow: StateFlow<Map<String, PremiumType>>
}

enum class PremiumType {
    NONE, TRIAL, COSA, PIRATE;

    fun isPremium(): Boolean {
        return this != NONE
    }
}
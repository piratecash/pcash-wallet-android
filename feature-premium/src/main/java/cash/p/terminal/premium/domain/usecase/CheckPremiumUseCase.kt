package cash.p.terminal.premium.domain.usecase

interface CheckPremiumUseCase {
    fun isPremium(): Boolean
    fun startAccountMonitorUpdate()
    suspend fun update(): Boolean
}
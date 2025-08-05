package cash.p.terminal.premium.domain.usecase

interface CheckPremiumUseCase {
    suspend operator fun invoke(): Boolean
} 
package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.CaptchaResponse
import cash.p.terminal.feature.miniapp.data.api.PCashWalletRequestDto
import cash.p.terminal.feature.miniapp.data.api.PCashWalletResponseDto
import cash.p.terminal.feature.miniapp.data.api.VerifyCaptchaResponse
import cash.p.terminal.feature.miniapp.data.repository.CaptchaRepository

class CaptchaUseCase(
    private val captchaRepository: CaptchaRepository
) {
    suspend fun getCaptcha(jwt: String, endpoint: String): Result<CaptchaResponse> {
        return captchaRepository.getCaptcha(jwt, endpoint)
    }

    suspend fun verifyCaptcha(
        jwt: String,
        endpoint: String,
        code: String
    ): Result<VerifyCaptchaResponse> {
        return captchaRepository.verifyCaptcha(jwt, endpoint, code)
    }

    suspend fun submitPCashWallet(
        jwt: String,
        endpoint: String,
        request: PCashWalletRequestDto
    ): Result<PCashWalletResponseDto> {
        return captchaRepository.submitPCashWallet(jwt, endpoint, request)
    }
}

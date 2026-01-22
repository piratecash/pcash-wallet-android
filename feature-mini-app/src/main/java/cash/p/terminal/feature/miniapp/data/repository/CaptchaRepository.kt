package cash.p.terminal.feature.miniapp.data.repository

import cash.p.terminal.feature.miniapp.data.api.CaptchaResponse
import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.api.MiniAppApiException
import cash.p.terminal.feature.miniapp.data.api.PCashWalletRequestDto
import cash.p.terminal.feature.miniapp.data.api.PCashWalletResponseDto
import cash.p.terminal.feature.miniapp.data.api.VerifyCaptchaResponse
import timber.log.Timber

class CaptchaRepository(
    private val miniAppApi: MiniAppApi
) {
    suspend fun getCaptcha(jwt: String, endpoint: String): Result<CaptchaResponse> {
        return try {
            val response = miniAppApi.getCaptcha(jwt, endpoint)
            Result.success(response)
        } catch (e: MiniAppApiException) {
            Timber.e(e, "Failed to get captcha: ${e.statusCode}")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get captcha")
            Result.failure(e)
        }
    }

    suspend fun verifyCaptcha(
        jwt: String,
        endpoint: String,
        code: String
    ): Result<VerifyCaptchaResponse> {
        return try {
            val response = miniAppApi.verifyCaptcha(jwt, endpoint, code)
            Result.success(response)
        } catch (e: MiniAppApiException) {
            Timber.e(e, "Failed to verify captcha: ${e.statusCode}")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify captcha")
            Result.failure(e)
        }
    }

    suspend fun submitPCashWallet(
        jwt: String,
        endpoint: String,
        request: PCashWalletRequestDto
    ): Result<PCashWalletResponseDto> {
        return try {
            val response = miniAppApi.submitPCashWallet(jwt, endpoint, request)
            Result.success(response)
        } catch (e: MiniAppApiException) {
            Timber.e(e, "Failed to submit pcash wallet: ${e.statusCode}")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to submit pcash wallet")
            Result.failure(e)
        }
    }
}

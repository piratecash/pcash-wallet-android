package cash.p.terminal.network.zcash.domain.usecase

import java.time.LocalDate

interface GetZcashHeightUseCase {
    suspend operator fun invoke(date: LocalDate): ZcashHeightResult
    suspend fun getCurrentBlockHeight(): Long?
}

sealed class ZcashHeightResult {
    data class Success(val height: Long) : ZcashHeightResult()
    object NotFound : ZcashHeightResult()
    object NetworkError : ZcashHeightResult()
}

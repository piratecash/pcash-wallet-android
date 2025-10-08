package cash.p.terminal.network.zcash.domain.usecase

import java.time.LocalDate

interface GetZcashHeightUseCase {
    suspend operator fun invoke(date: LocalDate): Long?
}

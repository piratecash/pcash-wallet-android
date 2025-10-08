package cash.p.terminal.network.zcash.data

import cash.p.terminal.network.zcash.api.ZcashApi
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal class GetZcashHeightUseCaseImpl(
    private val zcashApi: ZcashApi,
    private val logger: Logger
) : GetZcashHeightUseCase {
    override suspend fun invoke(date: LocalDate): Long? = try {
        zcashApi.getBlockHeight(date)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        logger.log(date.toString(), error)
        null
    }
}

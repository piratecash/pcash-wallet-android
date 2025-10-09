package cash.p.terminal.network.zcash.data

import cash.p.terminal.network.zcash.api.ZcashApi
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import java.time.LocalDate

internal class GetZcashHeightUseCaseImpl(
    private val zcashApi: ZcashApi,
    private val logger: Logger
) : GetZcashHeightUseCase {
    override suspend fun invoke(date: LocalDate): Long? = try {
        zcashApi.getBlockHeight(date)
    } catch (error: Throwable) {
        logger.log(date.toString(), error)
        null
    }

    override suspend fun getCurrentBlockHeight(): Long? = try {
        zcashApi.getLatestBlockHeight()
    } catch (error: Throwable) {
        logger.log("current", error)
        null
    }
}

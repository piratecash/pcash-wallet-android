package cash.p.terminal.network.zcash.data

import cash.p.terminal.network.zcash.api.ZcashApi
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.ZcashHeightResult
import java.time.LocalDate

internal class GetZcashHeightUseCaseImpl(
    private val zcashApi: ZcashApi,
    private val logger: Logger
) : GetZcashHeightUseCase {
    override suspend fun invoke(date: LocalDate): ZcashHeightResult = try {
        val height = zcashApi.getBlockHeight(date)
        if (height == null) {
            ZcashHeightResult.NotFound
        } else {
            ZcashHeightResult.Success(height)
        }
    } catch (error: Throwable) {
        logger.log(date.toString(), error)
        ZcashHeightResult.NetworkError
    }

    override suspend fun getCurrentBlockHeight(): Long? = try {
        zcashApi.getLatestBlockHeight()
    } catch (error: Throwable) {
        logger.log("current", error)
        null
    }
}

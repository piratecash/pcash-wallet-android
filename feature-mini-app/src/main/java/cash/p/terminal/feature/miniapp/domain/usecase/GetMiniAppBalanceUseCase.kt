package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.BalanceRequestDto
import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import java.math.BigDecimal

class GetMiniAppBalanceUseCase(
    private val miniAppApi: MiniAppApi,
    private val codeStorage: IUniqueCodeStorage
) {
    suspend operator fun invoke(): BigDecimal? {
        val uniqueCode = codeStorage.uniqueCode
        val evmAddress = codeStorage.connectedEvmAddress
        val endpoint = codeStorage.connectedEndpoint

        if (uniqueCode.isBlank() || evmAddress.isBlank() || endpoint.isBlank()) return null

        return runCatching {
            val response = miniAppApi.getWalletBalance(
                endpoint = endpoint,
                request = BalanceRequestDto(uniqueCode, evmAddress)
            )
            response.balance.toBigDecimalOrNull()?.movePointLeft(8)
        }.getOrNull()
    }
}

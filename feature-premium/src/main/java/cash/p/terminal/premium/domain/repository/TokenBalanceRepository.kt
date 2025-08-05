package cash.p.terminal.premium.domain.repository

import cash.p.terminal.premium.data.api.EthereumRpcApi
import cash.p.terminal.premium.domain.model.TokenBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TokenBalanceRepository(
    private val ethereumRpcApi: EthereumRpcApi
) {
    suspend fun getTokenBalance(
        rpcUrl: String,
        contractAddress: String,
        walletAddress: String
    ): TokenBalance? = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val balanceHex = ethereumRpcApi.getTokenBalance(rpcUrl, contractAddress, walletAddress)

            balanceHex?.let { hex ->
                val decimals = TokenBalance.PIRATE_DECIMALS

                TokenBalance.fromHexBalance(
                    hexBalance = hex,
                    decimals = decimals,
                )
            }
        }.getOrNull()
    }
}
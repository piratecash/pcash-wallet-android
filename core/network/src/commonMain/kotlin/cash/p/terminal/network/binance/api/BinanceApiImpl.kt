package cash.p.terminal.network.binance.api

import cash.p.terminal.network.binance.data.TokenBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BinanceApiImpl(
    private val ethereumRpcApi: EthereumRpcApi
): BinanceApi {
    private companion object Companion {
        const val BINANCE_BSC_URL = "https://bsc-dataseed.binance.org/"
    }

    override suspend fun getTokenBalance(
        contractAddress: String,
        walletAddress: String
    ): TokenBalance? = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val balanceHex = ethereumRpcApi.getTokenBalance(BINANCE_BSC_URL, contractAddress, walletAddress)

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
package cash.p.terminal.network.binance.api

import cash.p.terminal.network.binance.data.TokenBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class BinanceApiImpl(
    private val ethereumRpcApi: EthereumRpcApi
) : BinanceApi {
    private companion object Companion {
        val BINANCE_BSC_URL_LIST = listOf(
            "https://bsc-dataseed.binance.org/",
            "https://bsc-dataseed1.defibit.io/",
            "https://bsc-dataseed1.ninicoin.io/",
            "https://bsc-dataseed2.defibit.io/",
            "https://bsc-dataseed3.defibit.io/",
            "https://bsc-dataseed4.defibit.io/",
            "https://bsc-dataseed2.ninicoin.io/",
            "https://bsc-dataseed3.ninicoin.io/",
            "https://bsc-dataseed4.ninicoin.io/",
            "https://bsc-dataseed1.binance.org/",
            "https://bsc-dataseed2.binance.org/",
            "https://bsc-dataseed3.binance.org/",
            "https://bsc-dataseed4.binance.org/"
        )
    }

    override suspend fun getTokenBalance(
        contractAddress: String,
        walletAddress: String
    ): TokenBalance? = withContext(Dispatchers.IO) {
        for (url in BINANCE_BSC_URL_LIST) {
            try {
                val balanceHex =
                    ethereumRpcApi.getTokenBalance(url, contractAddress, walletAddress)

                balanceHex?.let { hex ->
                    val decimals = TokenBalance.PIRATE_DECIMALS

                    return@withContext TokenBalance.fromHexBalance(
                        hexBalance = hex,
                        decimals = decimals,
                    )
                }
            } catch (throwable: Throwable) {
                Timber.tag("BinanceApi").d(throwable, "getTokenBalance error for url: $url")
            }
        }
        return@withContext null
    }
}
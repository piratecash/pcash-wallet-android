package cash.p.terminal.network.binance.api

import cash.p.terminal.network.binance.data.TokenBalance

interface BinanceApi{
    suspend fun getTokenBalance(
        contractAddress: String,
        walletAddress: String
    ): TokenBalance?
}
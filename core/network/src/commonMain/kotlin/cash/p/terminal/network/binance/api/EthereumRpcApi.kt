package cash.p.terminal.network.binance.api

interface EthereumRpcApi {
    suspend fun getTokenBalance(
        rpcUrl: String,
        contractAddress: String,
        walletAddress: String
    ): String?

    suspend fun getCode(
        rpcUrl: String,
        address: String
    ): String?
}
package cash.p.terminal.network.binance.api

interface SolanaRpcApi {
    suspend fun getAccountInfo(address: String): SolanaAccountInfo?
}

data class SolanaAccountInfo(
    val executable: Boolean
)

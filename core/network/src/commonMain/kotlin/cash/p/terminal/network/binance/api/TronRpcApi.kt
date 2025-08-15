package cash.p.terminal.network.binance.api

interface TronRpcApi {
    suspend fun getContractInfo(address: String): TronContractInfo?
}

data class TronContractInfo(
    val contractMap: Map<String, Boolean>
)

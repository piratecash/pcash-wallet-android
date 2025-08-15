package cash.p.terminal.network.binance.api

interface TonRpcApi {
    suspend fun getAddressState(address: String): TonAddressState?
}

data class TonAddressState(
    val code: String
)

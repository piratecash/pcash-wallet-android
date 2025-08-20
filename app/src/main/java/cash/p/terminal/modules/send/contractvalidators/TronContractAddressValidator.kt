package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.network.binance.api.TronRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TronContractAddressValidator(
    private val tronRpcApi: TronRpcApi
) : ContractAddressValidator {

    override suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean? = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val contractInfo = tronRpcApi.getContractInfo(address)
            
            contractInfo?.contractMap?.isNotEmpty() == true
        }.getOrElse { null }
    }
}

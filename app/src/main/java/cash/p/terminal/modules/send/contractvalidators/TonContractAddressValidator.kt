package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.network.binance.api.TonRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TonContractAddressValidator(
    private val tonRpcApi: TonRpcApi
) : ContractAddressValidator {

    override suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean? =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                tonRpcApi.getAddressState(address)?.let {
                    it.code.isNotEmpty()
                }
            }.getOrElse { null }
        }
}

package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.network.binance.api.SolanaRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SolanaContractAddressValidator(
    private val solanaRpcApi: SolanaRpcApi
) : ContractAddressValidator {

    override suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean? =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val accountInfo = solanaRpcApi.getAccountInfo(address)

                accountInfo?.executable == true
            }.getOrElse { null }
        }
}

package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.network.binance.api.EthereumRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EvmContractAddressValidator(
    private val ethereumRpcApi: EthereumRpcApi,
    private val excludedContractValidator: ExcludedContractValidator
) : ContractAddressValidator {

    override suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean? =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val rpcUrl = getRpcUrlForBlockchain(blockchainType)
                val code = ethereumRpcApi.getCode(rpcUrl, address)
                if (excludedContractValidator.isKnownNotContract(code, blockchainType)) {
                    return@runCatching false
                }
                code != null && code != "0x"
            }.getOrElse { null }
        }

    private fun getRpcUrlForBlockchain(blockchainType: BlockchainType): String {
        return when (blockchainType) {
            BlockchainType.Ethereum -> "https://eth.llamarpc.com"
            BlockchainType.BinanceSmartChain -> "https://bsc-dataseed.binance.org/"
            BlockchainType.Polygon -> "https://polygon-rpc.com"
            BlockchainType.Avalanche -> "https://avalanche-evm.publicnode.com"
            BlockchainType.Optimism -> "https://mainnet.optimism.io"
            BlockchainType.Base -> "https://base.llamarpc.com"
            BlockchainType.ZkSync -> "https://mainnet.era.zksync.io"
            BlockchainType.ArbitrumOne -> "https://arb1.arbitrum.io/rpc"
            BlockchainType.Gnosis -> "https://rpc.gnosischain.com"
            BlockchainType.Fantom -> "https://rpcapi.fantom.network/"
            else -> throw IllegalArgumentException("Unsupported blockchain type: $blockchainType")
        }
    }
}

package cash.p.terminal.modules.send.contractvalidators

import io.horizontalsystems.core.entities.BlockchainType

interface ContractAddressValidator {
    suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean?
}

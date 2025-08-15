package cash.p.terminal.core.factories

import cash.p.terminal.modules.send.contractvalidators.ContractAddressValidator
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.getKoin

object ContractValidatorFactory {
    fun get(blockchainType: BlockchainType): ContractAddressValidator? {
        return try {
            getKoin().get(named(blockchainType.uid))
        } catch (e: Exception) {
            null
        }
    }
}

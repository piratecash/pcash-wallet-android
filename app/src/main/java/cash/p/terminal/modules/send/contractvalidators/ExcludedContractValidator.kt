package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.core.toRawHexString
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.hexToByteArray
import java.security.MessageDigest

class ExcludedContractValidator {
    private companion object {
        val excludedNonContractMd5 = mapOf(
            BlockchainType.BinanceSmartChain to setOf(
                "5339f2a8f1390b276e5d8a0dda27bdbb",
                "35c578edf83f5b055a289c548ce64b34"
            )
        )
    }

    fun isKnownNotContract(code: String?, blockchainType: BlockchainType): Boolean {
        if (code.isNullOrEmpty()) return false

        val normalizedCode = code.trim().removePrefix("0x").hexToByteArray()

        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(normalizedCode).toRawHexString()

        return excludedNonContractMd5[blockchainType]?.contains(hash) == true
    }
}

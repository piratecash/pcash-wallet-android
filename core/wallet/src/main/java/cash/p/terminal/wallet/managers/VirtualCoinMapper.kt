package cash.p.terminal.wallet.managers

import io.horizontalsystems.core.entities.BlockchainType

data class VirtualCoinMapping(
    val realCoinCode: String,
    val blockchainType: BlockchainType,
    val virtualCoinUid: String
)

class VirtualCoinMapper {

    val allMappings: List<VirtualCoinMapping> = listOf(
        VirtualCoinMapping(
            realCoinCode = "BSC-USD",
            blockchainType = BlockchainType.BinanceSmartChain,
            virtualCoinUid = "tether"
        )
    )

    private val realCoinCodeIndex: Map<String, VirtualCoinMapping> by lazy {
        allMappings.associateBy { it.realCoinCode }
    }

    fun getVirtualCoinUidForRealCoinCode(realCoinCode: String): String? =
        realCoinCodeIndex[realCoinCode]?.virtualCoinUid
}

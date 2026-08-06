package cash.p.terminal.core.managers

import io.horizontalsystems.core.entities.BlockchainType

internal object BuiltInAddressLabels {
    val all = listOf(
        AddressLabelDefinition(
            blockchainType = BlockchainType.BinanceSmartChain,
            address = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436",
            label = "Token Bridge",
        )
    )
}

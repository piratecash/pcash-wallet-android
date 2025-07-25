package cash.p.terminal.modules.address

import cash.p.dogecoinkit.MainNetDogecoin
import cash.p.terminal.core.supported
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.MainNet
import io.horizontalsystems.dashkit.MainNetDash
import io.horizontalsystems.ecash.MainNetECash
import io.horizontalsystems.litecoinkit.MainNetLitecoin
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.cosantakit.MainNetCosanta
import io.horizontalsystems.piratecashkit.MainNetPirateCash

class AddressHandlerFactory(
    private val udnApiKey: String,
) {

    private fun parserChainHandlers(blockchainType: BlockchainType): List<IAddressHandler> {
        val addressHandlers = mutableListOf<IAddressHandler>()
        when (blockchainType) {
            BlockchainType.Bitcoin -> {
                val network = MainNet()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
                addressHandlers.add(AddressHandlerBech32(network, blockchainType))
            }

            BlockchainType.BitcoinCash -> {
                val network = MainNetBitcoinCash()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
                addressHandlers.add(AddressHandlerBitcoinCash(network, blockchainType))
            }

            BlockchainType.ECash -> {
                val network = MainNetECash()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
                addressHandlers.add(AddressHandlerBitcoinCash(network, blockchainType))
            }

            BlockchainType.Litecoin -> {
                val network = MainNetLitecoin()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
                addressHandlers.add(AddressHandlerBech32(network, blockchainType))
            }

            BlockchainType.Dogecoin -> {
                val network = MainNetDogecoin()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
            }

            BlockchainType.Monero -> {
                addressHandlers.add(AddressHandlerMonero())
            }

            BlockchainType.Cosanta -> {
                val network = MainNetCosanta()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
            }

            BlockchainType.PirateCash -> {
                val network = MainNetPirateCash()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
            }

            BlockchainType.Dash -> {
                val network = MainNetDash()
                addressHandlers.add(AddressHandlerBase58(network, blockchainType))
            }

            BlockchainType.Zcash -> {
                addressHandlers.add(AddressHandlerZcash())
            }

            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.ArbitrumOne -> {
                addressHandlers.add(AddressHandlerEvm(blockchainType))
            }

            BlockchainType.Solana -> {
                addressHandlers.add(AddressHandlerSolana())
            }

            BlockchainType.Tron -> {
                addressHandlers.add(AddressHandlerTron())
            }

            BlockchainType.Ton -> {
                addressHandlers.add(AddressHandlerTon())
            }

            BlockchainType.Stellar -> {
                addressHandlers.add(AddressHandlerStellar())
            }

            is BlockchainType.Unsupported -> {
            }
        }
        return addressHandlers
    }

    private fun domainHandlers(blockchainType: BlockchainType): List<IAddressHandler> {
        val udnHandler = AddressHandlerUdn(TokenQuery(blockchainType, TokenType.Native), null, udnApiKey)
        val domainAddressHandlers = mutableListOf<IAddressHandler>(udnHandler)
        when (blockchainType) {
            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.ArbitrumOne -> {
                domainAddressHandlers.add(AddressHandlerEns(blockchainType, EnsResolverHolder.resolver))
            }

            else -> {}
        }
        return domainAddressHandlers
    }

    fun parserChain(blockchainType: BlockchainType?, withEns: Boolean = false): AddressParserChain {
        val addressHandlers = mutableListOf<IAddressHandler>()
        val domainHandlers = mutableListOf<IAddressHandler>()

        blockchainType?.let {
            addressHandlers.addAll(parserChainHandlers(it))
            if (withEns) {
                domainHandlers.addAll(domainHandlers(it))
            }
        } ?: run {
            BlockchainType.supported.forEach {
                addressHandlers.addAll(parserChainHandlers(it))
                if (withEns) {
                    domainHandlers.addAll(domainHandlers(it))
                }
            }
        }

        return AddressParserChain(addressHandlers, domainHandlers)
    }

    fun parserChain(blockchainTypes: List<BlockchainType>, blockchainTypesWithEns: List<BlockchainType>): AddressParserChain {
        val addressHandlers = mutableListOf<IAddressHandler>()
        val domainHandlers = mutableListOf<IAddressHandler>()

        for (blockchainType in blockchainTypes) {
            addressHandlers.addAll(parserChainHandlers(blockchainType))
        }

        for (blockchainType in blockchainTypesWithEns) {
            domainHandlers.addAll(domainHandlers(blockchainType))
        }

        return AddressParserChain(addressHandlers, domainHandlers)
    }

}
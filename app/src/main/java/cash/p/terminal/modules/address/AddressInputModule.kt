package cash.p.terminal.modules.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery

object AddressInputModule {

    class FactoryToken(private val tokenQuery: TokenQuery, private val coinCode: String, private val initial: Address?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ensHandler = AddressHandlerEns(tokenQuery.blockchainType, EnsResolverHolder.resolver)
            val udnHandler = AddressHandlerUdn(tokenQuery, coinCode, App.appConfigProvider.udnApiKey)
            val addressParserChain = AddressParserChain(domainHandlers = listOf(ensHandler, udnHandler))

            when (tokenQuery.blockchainType) {
                BlockchainType.Bitcoin,
                BlockchainType.BitcoinCash,
                BlockchainType.ECash,
                BlockchainType.Litecoin,
                BlockchainType.Dogecoin,
                BlockchainType.Cosanta,
                BlockchainType.PirateCash,
                BlockchainType.Dash,
                BlockchainType.Zcash -> {
                    addressParserChain.addHandler(AddressHandlerPure(tokenQuery.blockchainType))
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
                    addressParserChain.addHandler(AddressHandlerEvm(tokenQuery.blockchainType))
                }
                BlockchainType.Monero -> {
                    addressParserChain.addHandler(AddressHandlerMonero())
                }
                BlockchainType.Solana -> {
                    addressParserChain.addHandler(AddressHandlerSolana())
                }
                BlockchainType.Tron -> {
                    addressParserChain.addHandler(AddressHandlerTron())
                }
                BlockchainType.Ton -> {
                    addressParserChain.addHandler(AddressHandlerTon())
                }

                BlockchainType.Stellar -> {
                    addressParserChain.addHandler(AddressHandlerStellar())
                }

                is BlockchainType.Unsupported -> Unit
            }

            val addressUriParser = AddressUriParser(tokenQuery.blockchainType, tokenQuery.tokenType)
            val addressViewModel = AddressViewModel(
                blockchainType = tokenQuery.blockchainType,
                contactsRepository = App.contactsRepository,
                addressUriParser = addressUriParser,
                addressParserChain = addressParserChain,
                initial = initial
            )

            return addressViewModel as T
        }
    }

    class FactoryNft(private val blockchainType: BlockchainType) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ensHandler = AddressHandlerEns(blockchainType, EnsResolverHolder.resolver)
            val addressParserChain = AddressParserChain(domainHandlers = listOf(ensHandler))

            when (blockchainType) {
                BlockchainType.Bitcoin,
                BlockchainType.BitcoinCash,
                BlockchainType.ECash,
                BlockchainType.Litecoin,
                BlockchainType.Dogecoin,
                BlockchainType.Cosanta,
                BlockchainType.PirateCash,
                BlockchainType.Dash,
                BlockchainType.Zcash -> {
                    addressParserChain.addHandler(AddressHandlerPure(blockchainType))
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
                    addressParserChain.addHandler(AddressHandlerEvm(blockchainType))
                }
                BlockchainType.Solana,
                BlockchainType.Tron,
                BlockchainType.Ton,
                BlockchainType.Monero,
                BlockchainType.Stellar,
                is BlockchainType.Unsupported -> Unit

            }

            val addressUriParser = AddressUriParser(blockchainType, null)
            val addressViewModel = AddressViewModel(
                blockchainType,
                App.contactsRepository,
                addressUriParser,
                addressParserChain,
                null
            )

            return addressViewModel as T
        }
    }

}

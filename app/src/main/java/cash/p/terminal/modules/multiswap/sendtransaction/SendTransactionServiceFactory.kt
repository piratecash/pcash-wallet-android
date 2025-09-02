package cash.p.terminal.modules.multiswap.sendtransaction

import android.util.Log
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.modules.multiswap.sendtransaction.services.BitcoinSendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceStellar
import cash.p.terminal.modules.multiswap.sendtransaction.services.SolanaSendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.services.TonSendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.services.TronSendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.services.ZCashSendTransactionService
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

object SendTransactionServiceFactory {
    fun create(token: Token): ISendTransactionService<*> = try {
        Log.d("SendTransactionServiceFactory", "create: $token")
        when (val tokenType = token.type) {
            is TokenType.Derived -> {
                when (token.blockchainType) {
                    BlockchainType.Bitcoin -> {
                        BitcoinSendTransactionService(token)
                    }

                    BlockchainType.Litecoin -> {
                        BitcoinSendTransactionService(token)
                    }

                    else -> throw UnsupportedException("Unsupported token type: $tokenType")
                }
            }

            is TokenType.AddressTyped -> {
                if (token.blockchainType == BlockchainType.BitcoinCash) {
                    BitcoinSendTransactionService(token)
                } else throw UnsupportedException("Unsupported token type: $tokenType")
            }

            is TokenType.AddressSpecTyped -> {
                if (token.blockchainType == BlockchainType.Zcash) {
                    ZCashSendTransactionService(token)
                } else throw UnsupportedException("Unsupported token type: $tokenType")
            }

            TokenType.Native -> when (token.blockchainType) {
                BlockchainType.Dash,
                BlockchainType.Dogecoin,
                BlockchainType.ECash -> {
                    BitcoinSendTransactionService(token)
                }

                BlockchainType.Zcash -> {
                    ZCashSendTransactionService(token)
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
                    SendTransactionServiceEvm(token)
                }

                BlockchainType.Solana -> {
                    SolanaSendTransactionService(token)
                }

                BlockchainType.Tron -> {
                    TronSendTransactionService(token)
                }

                BlockchainType.Stellar -> {
                    val accountManager: IAccountManager by inject(IAccountManager::class.java)
                    val activeAccount = accountManager.activeAccount ?: throw IllegalStateException("No active account")
                    SendTransactionServiceStellar(account = activeAccount, token = token)
                }

                BlockchainType.Ton -> {
                    TonSendTransactionService(token)
                }

                else -> throw UnsupportedException("Unsupported token type: $tokenType")
            }

            is TokenType.Eip20 -> {
                if (token.blockchainType == BlockchainType.Tron) {
                    TronSendTransactionService(token)
                } else {
                    SendTransactionServiceEvm(token)
                }
            }

            is TokenType.Spl -> SolanaSendTransactionService(token)
            is TokenType.Jetton -> TonSendTransactionService(token)

            is TokenType.Asset,
            is TokenType.Unsupported -> throw UnsupportedException("Unsupported token type: $tokenType")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw UnsupportedException(e.message ?: "")
    }
}

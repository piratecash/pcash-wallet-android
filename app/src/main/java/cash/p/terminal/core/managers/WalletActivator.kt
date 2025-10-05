package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import cash.p.terminal.wallet.WalletFactory
import kotlinx.coroutines.runBlocking

class WalletActivator(
    private val walletManager: IWalletManager,
    private val marketKit: MarketKitWrapper,
    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase,
    private val walletFactory: WalletFactory,
) {
    @Deprecated("Use activateWalletsSuspended instead")
    fun activateWallets(account: Account, tokenQueries: List<TokenQuery>) {
        val wallets = tokenQueries.mapNotNull { tokenQuery ->
            marketKit.token(tokenQuery)?.let { token ->
                val hardwarePublicKey =
                    runBlocking { getHardwarePublicKeyForWalletUseCase(account, tokenQuery) }
                walletFactory.create(token, account, hardwarePublicKey)
            }
        }

        walletManager.save(wallets)
    }

    suspend fun activateWalletsSuspended(account: Account, tokenQueries: List<TokenQuery>) {
        val wallets = tokenQueries.mapNotNull { tokenQuery ->
            marketKit.token(tokenQuery)?.let { token ->
                walletFactory.create(
                    token,
                    account,
                    getHardwarePublicKeyForWalletUseCase(account, tokenQuery)
                )
            }
        }

        walletManager.saveSuspended(wallets)
    }

    fun activateTokens(account: Account, tokens: List<Token>) {
        val wallets = mutableListOf<Wallet>()

        for (token in tokens) {
            val hardwarePublicKey =
                runBlocking { getHardwarePublicKeyForWalletUseCase(account, token) }
            walletFactory.create(token, account, hardwarePublicKey)?.let(wallets::add)
        }

        walletManager.save(wallets)
    }

}

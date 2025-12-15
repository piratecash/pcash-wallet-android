package cash.p.terminal.core.factories

import cash.p.terminal.core.managers.EvmAccountManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.entities.BlockchainType

class EvmAccountManagerFactory(
    private val accountManager: cash.p.terminal.wallet.IAccountManager,
    private val walletManager: IWalletManager,
    private val marketKit: MarketKitWrapper,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager
) {

    fun evmAccountManager(blockchainType: BlockchainType, evmKitManager: EvmKitManager) =
        EvmAccountManager(
            blockchainType,
            accountManager,
            walletManager,
            marketKit,
            evmKitManager,
            tokenAutoEnableManager,
            userDeletedWalletManager
        )

}

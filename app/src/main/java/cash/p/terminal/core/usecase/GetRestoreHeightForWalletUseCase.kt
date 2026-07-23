package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.withContext

/**
 * Resolves the restore (birthday) block height stored for a wallet, if the wallet's
 * blockchain supports restoring by height.
 */
class GetRestoreHeightForWalletUseCase(
    private val restoreSettingsManager: RestoreSettingsManager,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(wallet: Wallet): Long? = withContext(dispatcherProvider.io) {
        when (wallet.token.blockchainType) {
            BlockchainType.Zcash ->
                restoreSettingsManager.settings(wallet.account, BlockchainType.Zcash).birthdayHeight

            BlockchainType.Monero ->
                restoreSettingsManager.settings(wallet.account, BlockchainType.Monero).birthdayHeight
                    ?: (wallet.account.type as? AccountType.MnemonicMonero)?.height

            else -> null
        }
    }
}

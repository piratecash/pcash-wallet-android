package cash.p.terminal.domain.usecase

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.ZcashNetwork
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClearZCashWalletDataUseCase(
    private val zcashSingleUseAddressStorage: ZcashSingleUseAddressStorage
) {

    companion object {
        private const val ALIAS_PREFIX = "zcash_"
    }

    private val mutex = Mutex()

    suspend operator fun invoke(accountId: String) {
        mutex.withLock {
            Synchronizer.erase(
                appContext = App.instance,
                network = ZcashNetwork.Mainnet,
                alias = getValidAliasFromAccountId(accountId, null)
            )
            AddressSpecType.entries.forEach {
                Synchronizer.erase(
                    appContext = App.instance,
                    network = ZcashNetwork.Mainnet,
                    alias = getValidAliasFromAccountId(accountId, it)
                )
            }
            zcashSingleUseAddressStorage.deleteAccountAddresses(accountId)
        }
    }

    fun getValidAliasFromAccountId(
        accountId: String,
        addressSpecTyped: AddressSpecType?
    ): String {
        return (ALIAS_PREFIX + accountId.replace("-", "_")).let {
            if (addressSpecTyped != null) {
                it + "_${addressSpecTyped.name}"
            } else {
                it
            }
        }
    }
}

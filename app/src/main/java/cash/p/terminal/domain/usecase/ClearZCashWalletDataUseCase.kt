package cash.p.terminal.domain.usecase

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.ZcashNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class ClearZCashWalletDataUseCase(
    private val zcashSingleUseAddressStorage: ZcashSingleUseAddressStorage
) {

    companion object {
        private const val ALIAS_PREFIX = "zcash_"
        private const val MAX_ERASE_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val mutex = Mutex()

    suspend operator fun invoke(accountId: String) {
        mutex.withLock {
            eraseWithRetry(getValidAliasFromAccountId(accountId, null))
            AddressSpecType.entries.forEach {
                eraseWithRetry(getValidAliasFromAccountId(accountId, it))
            }
            zcashSingleUseAddressStorage.deleteAccountAddresses(accountId)
        }
    }

    private suspend fun eraseWithRetry(alias: String) {
        repeat(MAX_ERASE_RETRIES) { attempt ->
            try {
                Synchronizer.erase(
                    appContext = App.instance,
                    network = ZcashNetwork.Mainnet,
                    alias = alias
                )
                return
            } catch (e: IllegalStateException) {
                // Another synchronizer with the same key is still active
                // This can happen due to race condition when adapter is being stopped
                if (attempt < MAX_ERASE_RETRIES - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    Timber.d("Synchronizer still active for alias $alias, retrying in ${delayMs}ms (attempt ${attempt + 1}/$MAX_ERASE_RETRIES)")
                    delay(delayMs)
                } else {
                    Timber.w(e, "Failed to erase synchronizer for alias $alias after $MAX_ERASE_RETRIES attempts")
                }
            }
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

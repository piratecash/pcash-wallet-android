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

enum class ZcashEraseResult { ALL, PARTIAL, NONE }

class ClearZCashWalletDataUseCase(
    private val zcashSingleUseAddressStorage: ZcashSingleUseAddressStorage
) {

    companion object {
        private const val ALIAS_PREFIX = "zcash_"
        private const val MAX_ERASE_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val mutex = Mutex()

    /**
     * Erases every Zcash alias belonging to [accountId]. Erases are per-alias and irreversible,
     * so the caller must distinguish [ZcashEraseResult.NONE] (a true unchanged no-op that is safe
     * to roll back) from [ZcashEraseResult.PARTIAL]/[ZcashEraseResult.ALL] (the account has already
     * lost data and must be treated as committed to a fresh restore, never resumed as-is).
     */
    suspend operator fun invoke(accountId: String): ZcashEraseResult = mutex.withLock {
        val aliases = listOf(getValidAliasFromAccountId(accountId, null)) +
                AddressSpecType.entries.map { getValidAliasFromAccountId(accountId, it) }
        val erasedCount = aliases.count { eraseWithRetry(it) }
        val result = when (erasedCount) {
            0 -> ZcashEraseResult.NONE
            aliases.size -> ZcashEraseResult.ALL
            else -> ZcashEraseResult.PARTIAL
        }
        // Only a clean no-op leaves the account unchanged; any erase commits it to a restore, so
        // drop the single-use addresses too.
        if (result != ZcashEraseResult.NONE) {
            zcashSingleUseAddressStorage.deleteAccountAddresses(accountId)
        }
        result
    }

    private suspend fun eraseWithRetry(alias: String): Boolean {
        repeat(MAX_ERASE_RETRIES) { attempt ->
            try {
                Synchronizer.erase(
                    appContext = App.instance,
                    network = ZcashNetwork.Mainnet,
                    alias = alias
                )
                return true
            } catch (e: IllegalStateException) {
                // Another synchronizer with the same key is still active
                // This can happen due to race condition when adapter is being stopped
                if (attempt < MAX_ERASE_RETRIES - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    Timber.d(
                        "Synchronizer still active for alias $alias, retrying in ${delayMs}ms " +
                                "(attempt ${attempt + 1}/$MAX_ERASE_RETRIES)"
                    )
                    delay(delayMs)
                } else {
                    Timber.w(e, "Failed to erase synchronizer for alias $alias after $MAX_ERASE_RETRIES attempts")
                }
            }
        }
        return false
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

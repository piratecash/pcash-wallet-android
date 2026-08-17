package cash.p.terminal.wallet

import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IAccountManager {
    val hasNonStandardAccount: Boolean
    val activeAccount: Account?
    val activeAccountStateFlow: Flow<ActiveAccountState>
    val isAccountsEmpty: Boolean
    val accounts: List<Account>
    val accountsFlow: Flow<List<Account>>
    val accountsDeletedFlowable: Flowable<Unit>
    val newAccountBackupRequiredFlow: StateFlow<Account?>

    fun setActiveAccountId(activeAccountId: String?)
    fun updateSignedHashes(signedHashes: Int)
    fun account(id: String): Account?
    fun save(account: Account, updateActive: Boolean = true)
    fun import(accounts: List<Account>)
    fun update(account: Account)
    fun updateName(id: String, name: String)
    fun markAsBackedUp(id: String)
    fun markAsFileBackedUp(id: String)
    suspend fun delete(id: String)
    fun clear()
    fun getDeletedAccountIds(): List<String>
    fun clearDeleted(accountIds: List<String>)
    fun onHandledBackupRequiredNewAccount()
    fun setLevel(level: Int)
    fun updateAccountLevels(accountIds: List<String>, level: Int)
    fun updateMaxLevel(level: Int)
    fun accountsAtLevel(level: Int): List<Account>
}

/**
 * Returns the latest persisted snapshot of [account], falling back to the given instance when the
 * cache has no entry. Used after a hardware scan may have healed the account's model (e.g. a legacy
 * Trezor Safe 3 stored as "unknown") so callers apply the healed model within the same operation
 * instead of the stale pre-scan snapshot, which would gate out Tron/Solana.
 */
fun IAccountManager.latestAccountOr(account: Account): Account = account(account.id) ?: account

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
    fun clearDeleted()
    fun onHandledBackupRequiredNewAccount()
    fun setLevel(level: Int)
    fun updateAccountLevels(accountIds: List<String>, level: Int)
    fun updateMaxLevel(level: Int)
    fun accountsAtLevel(level: Int): List<Account>
}
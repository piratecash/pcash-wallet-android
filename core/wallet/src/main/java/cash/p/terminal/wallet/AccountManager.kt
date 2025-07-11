package cash.p.terminal.wallet

import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.horizontalsystems.core.logger.AppLogger
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountManager(
    private val storage: IAccountsStorage,
    private val accountCleaner: IAccountCleaner,
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase
) : IAccountManager {
    private val logger: AppLogger = AppLogger("AccountManager")

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var accountsCache = mutableMapOf<String, Account>()
    private val accountsSubject = PublishSubject.create<List<Account>>()
    private val accountsDeletedSubject = PublishSubject.create<Unit>()
    private val _activeAccountStateFlow =
        MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
    private var currentLevel = Int.MAX_VALUE

    override val activeAccountStateFlow = _activeAccountStateFlow

    override val hasNonStandardAccount: Boolean
        get() = accountsCache.any { it.value.nonStandard }

    override var activeAccount: Account? = null

    override val isAccountsEmpty: Boolean
        get() = storage.isAccountsEmpty

    override val accounts: List<Account>
        get() = accountsCache.map { it.value }

    override val accountsFlowable: Flowable<List<Account>>
        get() = accountsSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val accountsDeletedFlowable: Flowable<Unit>
        get() = accountsDeletedSubject.toFlowable(BackpressureStrategy.BUFFER)

    private val _newAccountBackupRequiredFlow = MutableStateFlow<Account?>(null)
    override val newAccountBackupRequiredFlow = _newAccountBackupRequiredFlow.asStateFlow()

    private fun updateCache(account: Account) {
        accountsCache[account.id] = account
    }

    override fun setActiveAccountId(activeAccountId: String?) {
        if (activeAccount?.id != activeAccountId || (activeAccount == null && activeAccountId == null)) {
            storage.setActiveAccountId(currentLevel, activeAccountId)
            activeAccount = activeAccountId?.let { account(it) }
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(activeAccount)
            }
        }
    }

    override fun updateSignedHashes(signedHashes: Int) {
        activeAccount?.let {
            val hardwareCard = it.type as? AccountType.HardwareCard ?: return
            val updatedAccount = it.copy(
                type = hardwareCard.copy(
                    signedHashes = signedHashes
                )
            )
            storage.update(updatedAccount)
            activeAccount = updatedAccount
            updateCache(updatedAccount)
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(updatedAccount)
            }
        }
    }

    override fun account(id: String): Account? {
        return accounts.find { account -> account.id == id }
    }

    override fun onHandledBackupRequiredNewAccount() {
        _newAccountBackupRequiredFlow.update { null }
    }

    override fun save(account: Account, updateActive: Boolean) {
        storage.save(account)

        updateCache(account)
        accountsSubject.onNext(accounts)

        if (updateActive) {
            setActiveAccountId(account.id)
        }
        if (!account.isBackedUp && !account.isFileBackedUp) {
            _newAccountBackupRequiredFlow.update {
                account
            }
        }
    }

    override fun import(accounts: List<Account>) {
        for (account in accounts) {
            storage.save(account)
            updateCache(account)
        }

        accountsSubject.onNext(accounts)

        if (activeAccount == null) {
            accounts.minByOrNull { it.name.lowercase() }?.let { account ->
                setActiveAccountId(account.id)
                if (!account.isBackedUp && !account.isFileBackedUp) {
                    _newAccountBackupRequiredFlow.update {
                        account
                    }
                }
            }
        }
    }

    override fun updateAccountLevels(accountIds: List<String>, level: Int) {
        storage.updateLevels(accountIds, level)
    }

    override fun updateMaxLevel(level: Int) {
        storage.updateMaxLevel(level)
    }

    override fun update(account: Account) {
        storage.update(account)

        updateCache(account)
        accountsSubject.onNext(accounts)

        activeAccount?.id?.let {
            if (account.id == it) {
                activeAccount = account
                _activeAccountStateFlow.update { ActiveAccountState.ActiveAccount(activeAccount) }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val accountToDelete = storage.loadAccount(id)
        if (accountToDelete?.type is AccountType.MnemonicMonero) {
            removeMoneroWalletFilesUseCase(accountToDelete.type.walletInnerName)
        }

        accountsCache.remove(id)
        storage.delete(id)

        accountsSubject.onNext(accounts)
        accountsDeletedSubject.onNext(Unit)

        if (id == activeAccount?.id) {
            setActiveAccountId(accounts.firstOrNull()?.id)
        }
    }

    override fun clear() {
        storage.clear()
        accountsCache.clear()
        accountsSubject.onNext(listOf())
        accountsDeletedSubject.onNext(Unit)
        setActiveAccountId(null)
    }

    override fun setLevel(level: Int) {
        accountsCache = storage.allAccounts(level).associateBy { it.id }.toMutableMap()
        val activeAccountIdForLevel = storage.getActiveAccountId(level)

        if (activeAccountIdForLevel != null && accountsCache.isEmpty() && currentLevel != Int.MAX_VALUE) {
            logger.info("Keystore problems, can't decode accounts, ignore account changing level")
            return // looks like we can't decode accounts due to Keystore problems(found on Android 11 Oppo devices)
        }
        currentLevel = level

        if (activeAccount == null || activeAccount?.id != activeAccountIdForLevel) {
            activeAccount = accountsCache[activeAccountIdForLevel] ?: accounts.firstOrNull()
            _activeAccountStateFlow.update {
                ActiveAccountState.ActiveAccount(activeAccount)
            }
        }
//        logger.info("setLevel: $level, activeAccount: ${activeAccount?.id}, activeAccountIdForLevel: $activeAccountIdForLevel, accounts: ${accounts.size}, accountsCache: ${accountsCache.size}")
        accountsSubject.onNext(accounts)
    }

    override fun clearAccounts() {
        coroutineScope.launch {
            delay(3000)
            accountCleaner.clearAccounts(storage.getDeletedAccountIds())
            storage.clearDeleted()
        }
    }

}

class NoActiveAccount : Exception()

sealed class ActiveAccountState {
    class ActiveAccount(val account: Account?) : ActiveAccountState()
    object NotLoaded : ActiveAccountState()
}
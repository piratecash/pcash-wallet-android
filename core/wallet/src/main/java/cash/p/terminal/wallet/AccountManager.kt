package cash.p.terminal.wallet

import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.horizontalsystems.core.logger.AppLogger
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AccountManager(
    private val storage: IAccountsStorage,
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase,
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase
) : IAccountManager {
    private val logger: AppLogger = AppLogger("AccountManager")

    private var accountsCache = mutableMapOf<String, Account>()
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

    private val _accountsSharedFlow = MutableSharedFlow<List<Account>>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override val accountsFlow: Flow<List<Account>>
        get() = _accountsSharedFlow.asSharedFlow()

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
        _accountsSharedFlow.tryEmit(accounts)

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

        _accountsSharedFlow.tryEmit(accounts)

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
        _accountsSharedFlow.tryEmit(accounts)

        activeAccount?.id?.let {
            if (account.id == it) {
                activeAccount = account
                _activeAccountStateFlow.update { ActiveAccountState.ActiveAccount(activeAccount) }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val accountToDelete = storage.loadAccount(id)
        accountToDelete?.let { account ->
            getMoneroWalletFilesNameUseCase(account)
        }?.also { walletFiles ->
            removeMoneroWalletFilesUseCase(walletFiles)
        }

        accountsCache.remove(id)
        storage.delete(id)

        _accountsSharedFlow.tryEmit(accounts)
        accountsDeletedSubject.onNext(Unit)

        if (id == activeAccount?.id) {
            setActiveAccountId(accounts.firstOrNull()?.id)
        }
    }

    override fun clear() {
        storage.clear()
        accountsCache.clear()
        _accountsSharedFlow.tryEmit(listOf())
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
        _accountsSharedFlow.tryEmit(accounts)
    }

    override fun getDeletedAccountIds() = storage.getDeletedAccountIds()
    override fun clearDeleted() = storage.clearDeleted()

    override fun accountsAtLevel(level: Int): List<Account> {
        return storage.allAccounts(level)
    }
}

class NoActiveAccount : Exception()

sealed class ActiveAccountState {
    class ActiveAccount(val account: Account?) : ActiveAccountState()
    object NotLoaded : ActiveAccountState()
}
package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.EnabledWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WalletManager(
    private val accountManager: IAccountManager,
    private val storage: IWalletStorage
) : IWalletManager {

    private val _activeWalletsState = MutableStateFlow<List<Wallet>>(emptyList())
    override val activeWalletsFlow: StateFlow<List<Wallet>> get() = _activeWalletsState.asStateFlow()

    private val walletsSet = mutableSetOf<Wallet>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mutex = Mutex()

    init {
        coroutineScope.launch {
            accountManager.activeAccountStateFlow.collectLatest { activeAccountState ->
                if (activeAccountState is ActiveAccountState.ActiveAccount) {
                    handleUpdatedSuspended(activeAccountState.account)
                } else {
                    handleUpdatedSuspended(null)
                }
            }
        }
    }

    override val activeWallets: List<Wallet>
        get() = _activeWalletsState.value

    override suspend fun saveSuspended(wallets: List<Wallet>) {
        handleSuspended(wallets, emptyList())
    }

    suspend fun deleteSuspended(wallets: List<Wallet>) {
        handleSuspended(emptyList(), wallets)
    }

    suspend fun handleSuspended(newWallets: List<Wallet>, deletedWallets: List<Wallet>) {
        mutex.withLock {
            if (newWallets.isNotEmpty()) storage.save(newWallets)
            if (deletedWallets.isNotEmpty()) storage.delete(deletedWallets)

            val activeAccount = accountManager.activeAccount
            walletsSet.addAll(newWallets.filter { it.account == activeAccount })
            walletsSet.removeAll(deletedWallets)

            notifyActiveWalletsLocked()
        }
    }

    private suspend fun handleUpdatedSuspended(activeAccount: Account?) {
        mutex.withLock {
            val activeWallets =
                activeAccount?.let { withContext(Dispatchers.IO) { storage.wallets(it) } }
                    ?: listOf()
            walletsSet.clear()
            walletsSet.addAll(activeWallets)
            notifyActiveWalletsLocked()
        }
    }

    override fun save(wallets: List<Wallet>) {
        coroutineScope.launch {
            try {
                handleSuspended(wallets, emptyList())
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun delete(wallets: List<Wallet>) {
        coroutineScope.launch {
            try {
                handleSuspended(emptyList(), wallets)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Use suspend functions instead")
    override fun handle(newWallets: List<Wallet>, deletedWallets: List<Wallet>) {
        coroutineScope.launch {
            handleSuspended(newWallets, deletedWallets)
        }
    }

    override fun clear() {
        val clearJob = coroutineScope.launch {
            mutex.withLock {
                withContext(Dispatchers.IO) { storage.clear() }
                walletsSet.clear()
                notifyActiveWalletsLocked()
            }
        }

        coroutineScope.launch {
            clearJob.join()
            coroutineScope.coroutineContext.cancelChildren()
        }
    }

    override fun saveEnabledWallets(enabledWallets: List<EnabledWallet>) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    storage.handle(enabledWallets)
                }
                val active = accountManager.activeAccount
                val activeWallets =
                    active?.let { withContext(Dispatchers.IO) { storage.wallets(it) } } ?: listOf()
                walletsSet.clear()
                walletsSet.addAll(activeWallets)
                notifyActiveWalletsLocked()
            }
        }
    }

    private fun notifyActiveWalletsLocked() {
        val snapshot = walletsSet.toList()
        _activeWalletsState.value = snapshot
    }

    override fun getWallets(account: Account): List<Wallet> {
        return storage.wallets(account)
    }
}

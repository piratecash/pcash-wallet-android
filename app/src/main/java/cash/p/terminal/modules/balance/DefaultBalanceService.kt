package cash.p.terminal.modules.balance

import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.balance.BalanceXRateRepository
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class DefaultBalanceService private constructor(
    private val activeWalletRepository: BalanceActiveWalletRepository,
    private val xRateRepository: BalanceXRateRepository,
    private val adapterRepository: BalanceAdapterRepository,
    private val localStorage: ILocalStorage,
    private val connectivityManager: ConnectivityManager,
    private val balanceSorter: BalanceSorter,
    private val accountManager: IAccountManager
) : BalanceService, AutoCloseable {

    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase by inject(
        RemoveMoneroWalletFilesUseCase::class.java
    )
    private val moneroFileDao: MoneroFileDao by inject(
        MoneroFileDao::class.java
    )

    override val networkAvailable: Boolean
        get() = connectivityManager.isConnected.value
    override val baseCurrency by xRateRepository::baseCurrency

    override var sortType: BalanceSortType
        get() = localStorage.sortType
        set(value) {
            localStorage.sortType = value
            // Re-sort current list
            updateBalanceItems { currentItems ->
                currentItems // just return current items, sorting will happen automatically
            }
        }

    private var _isWatchAccount = false

    override val isWatchAccount: Boolean
        get() = _isWatchAccount

    override val account: Account?
        get() = accountManager.activeAccount

    private var hideZeroBalances = false
    private var started: Boolean = false

    // Replace CopyOnWriteArrayList with StateFlow
    private val _balanceItemsState = MutableStateFlow<List<BalanceItem>>(emptyList())
    override val balanceItemsFlow = _balanceItemsState.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun start() {
        if (started) return
        started = true

        coroutineScope.launch {
            activeWalletRepository.itemsObservable.asFlow().collect { wallets ->
                handleWalletsUpdate(wallets)
            }
        }
        coroutineScope.launch {
            xRateRepository.itemObservable.asFlow().collect { latestRates ->
                handleXRateUpdate(latestRates)
            }
        }
        coroutineScope.launch {
            adapterRepository.readyObservable.asFlow().collect {
                handleAdaptersReady()
            }
        }
        coroutineScope.launch {
            adapterRepository.updatesObservable.asFlow().collect {
                handleAdapterUpdate(it)
            }
        }
    }

    /**
     * Safe balance list update with automatic sorting and filtering
     */
    private fun updateBalanceItems(
        transform: (current: List<BalanceItem>) -> List<BalanceItem>
    ) {
        _balanceItemsState.update { currentItems ->
            val updatedItems = transform(currentItems)
            // First deduplicate by contract address (for virtual + real tokens like USDT/BSC-USD)
            val contractDeduped = deduplicateByContract(updatedItems)
            val uniqueItems = contractDeduped.distinctBy { it.wallet.token.tokenQuery.id }
            val sortedItems = balanceSorter.sort(uniqueItems, sortType)
            getFilteredItems(sortedItems)
        }
    }

    /**
     * Deduplicate tokens with same contract address on same blockchain.
     * Keeps the first added token (virtual USDT or real BSC-USD).
     */
    private fun deduplicateByContract(items: List<BalanceItem>): List<BalanceItem> {
        return items
            .groupBy { extractContractKey(it.wallet) }
            .map { (_, group) -> group.first() }
    }

    private fun extractContractKey(wallet: Wallet): String {
        val address = (wallet.token.type as? TokenType.Eip20)?.address
            ?: return wallet.token.tokenQuery.id
        return "${wallet.token.blockchainType.uid}|$address"
    }

    /**
     * Applies filtering for zero balances
     */
    private fun getFilteredItems(items: List<BalanceItem>): List<BalanceItem> {
        return if (hideZeroBalances) {
            items.filter { it.wallet.token.type.isNative || it.balanceData.total > BigDecimal.ZERO }
        } else {
            items
        }
    }

    private fun handleAdaptersReady() {
        updateBalanceItems { currentItems ->
            currentItems.map { balanceItem ->
                balanceItem.copy(
                    balanceData = adapterRepository.balanceData(balanceItem.wallet),
                    state = adapterRepository.state(balanceItem.wallet),
                    sendAllowed = adapterRepository.sendAllowed(balanceItem.wallet),
                )
            }
        }
    }

    private fun handleAdapterUpdate(wallet: Wallet) {
        updateBalanceItems { currentItems ->
            currentItems.map { item ->
                if (item.wallet == wallet) {
                    item.copy(
                        balanceData = adapterRepository.balanceData(wallet),
                        state = adapterRepository.state(wallet),
                        sendAllowed = adapterRepository.sendAllowed(wallet),
                    )
                } else {
                    item
                }
            }
        }
    }

    private fun handleXRateUpdate(latestRates: Map<String, CoinPrice?>) {
        updateBalanceItems { currentItems ->
            currentItems.map { balanceItem ->
                if (latestRates.containsKey(balanceItem.wallet.coin.uid)) {
                    balanceItem.copy(coinPrice = latestRates[balanceItem.wallet.coin.uid])
                } else {
                    balanceItem
                }
            }
        }
    }

    private fun handleWalletsUpdate(wallets: List<Wallet>) {
        // Update account state
        _isWatchAccount = accountManager.activeAccount?.isWatchAccount == true
        hideZeroBalances = accountManager.activeAccount?.type?.hideZeroBalances == true

        // Configure repositories
        adapterRepository.setWallet(wallets)
        xRateRepository.setCoinUids(wallets.map { it.coin.uid })
        val latestRates = xRateRepository.getLatestRates()

        // Complete replacement of balance list
        updateBalanceItems { _ ->
            wallets.map { wallet ->
                BalanceItem(
                    wallet = wallet,
                    balanceData = adapterRepository.balanceData(wallet),
                    state = adapterRepository.state(wallet),
                    sendAllowed = adapterRepository.sendAllowed(wallet),
                    coinPrice = latestRates[wallet.coin.uid]
                )
            }
        }
    }

    override suspend fun refresh() {
        xRateRepository.refresh()
        adapterRepository.refresh()
    }

    override val disabledWalletSubject = PublishSubject.create<Wallet>()

    override suspend fun disable(wallet: Wallet) {
        activeWalletRepository.disable(wallet)
        deleteMoneroRecords(wallet)
        disabledWalletSubject.onNext(wallet)
    }

    /**
     * Deletes Monero wallet files if the wallet is a Monero wallet and removes the associated record from the database.
     */
    private suspend fun deleteMoneroRecords(wallet: Wallet) = withContext(Dispatchers.IO) {
        if (removeMoneroWalletFilesUseCase(wallet.account)) {
            moneroFileDao.deleteAssociatedRecord(wallet.account.id)
        }
    }

    override fun enable(wallet: Wallet) {
        activeWalletRepository.enable(wallet)
    }

    override fun close() {
        coroutineScope.cancel()
        xRateRepository.clear()
        adapterRepository.clear()
        started = false
        // Clear state
        _balanceItemsState.value = emptyList()
    }

    companion object {
        private val userDeletedWalletManager: UserDeletedWalletManager by inject(UserDeletedWalletManager::class.java)

        fun getInstance(tag: String): DefaultBalanceService {
            return DefaultBalanceService(
                BalanceActiveWalletRepository(
                    App.walletManager,
                    userDeletedWalletManager,
                    App.evmSyncSourceManager
                ),
                DefaultBalanceXRateRepository(tag, App.currencyManager, App.marketKit),
                BalanceAdapterRepository(
                    App.adapterManager,
                    BalanceCache(App.appDatabase.enabledWalletsCacheDao())
                ),
                App.localStorage,
                App.connectivityManager,
                BalanceSorter(),
                App.accountManager
            )
        }
    }
}
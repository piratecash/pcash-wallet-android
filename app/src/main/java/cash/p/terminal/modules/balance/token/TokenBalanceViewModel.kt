package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.storage.toRecordUidMap
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.core.swappable
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.modules.balance.BackupRequiredError
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.balance.token.TokenBalanceModule.TokenBalanceUiState
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.modules.transactions.withClearedAmlStatus
import cash.p.terminal.modules.transactions.withUpdatedAmlStatus
import cash.p.terminal.network.pirate.domain.useCase.GetChangeNowAssociatedCoinTickerUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ADAPTER_AWAIT_TIMEOUT_MS = 5000L

class TokenBalanceViewModel(
    private val totalBalance: TotalBalance,
    private val wallet: Wallet,
    private val balanceService: TokenBalanceService,
    private val balanceViewItemFactory: BalanceViewItemFactory,
    private val transactionsService: TokenTransactionsService,
    private val transactionViewItem2Factory: TransactionViewItemFactory,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val connectivityManager: ConnectivityManager,
    private val accountManager: IAccountManager,
    private val transactionHiddenManager: TransactionHiddenManager,
    private val getChangeNowAssociatedCoinTickerUseCase: GetChangeNowAssociatedCoinTickerUseCase,
    private val premiumSettings: PremiumSettings,
    private val amlStatusManager: AmlStatusManager
) : ViewModelUiState<TokenBalanceUiState>() {

    private val logger = AppLogger("TokenBalanceViewModel-${wallet.coin.code}")
    private val updateSwapProviderTransactionsStatusUseCase: UpdateSwapProviderTransactionsStatusUseCase = getKoinInstance()
    private val adapterManager: IAdapterManager = getKoinInstance()
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage = getKoinInstance()

    private val title = wallet.token.coin.code + wallet.token.badge?.let { " ($it)" }.orEmpty()

    private var balanceViewItem: BalanceViewItem? = null
    private var transactions: Map<String, List<TransactionViewItem>>? = null
    private var hasHiddenTransactions: Boolean = false
    private var amlPromoAlertEnabled = premiumSettings.getAmlCheckShowAlert()

    // Maps transaction record UID to SwapProviderTransaction for reactive updates
    private var swapStatusMap = emptyMap<String, SwapProviderTransaction>()

    private var statusCheckerJob: Job? = null
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    var secondaryValue by mutableStateOf(DeemedValue(""))
        private set

    var refreshing by mutableStateOf(false)
        private set

    private var showCurrencyAsSecondary = true

    init {
        viewModelScope.launch {
            balanceService.balanceItemFlow.collect { balanceItem ->
                balanceItem?.let {
                    updateBalanceViewItem(
                        balanceItem = it,
                        isSwappable = isSwappable(it.wallet.token)
                    )
                }
            }
        }

        viewModelScope.launch {
            balanceHiddenManager.walletBalanceHiddenFlow(wallet.tokenQueryId).collect {
                balanceService.balanceItem?.let {
                    updateBalanceViewItem(
                        balanceItem = it,
                        isSwappable = isSwappable(it.wallet.token)
                    )
                    transactionViewItem2Factory.updateCache()
                    transactionsService.refreshList()
                }
            }
        }

        viewModelScope.launch {
            transactionsService.transactionItemsFlow.collect {
                updateTransactions(it)
            }
        }

        viewModelScope.launch {
            balanceService.start()
            transactionsService.start()
        }

        viewModelScope.launch {
            transactionHiddenManager.transactionHiddenFlow.collectLatest {
                transactionsService.refreshList()
                refreshTransactionsFromCache()
            }
        }

        viewModelScope.launch {
            totalBalance.stateFlow.collectLatest { totalBalanceValue ->
                updateSecondaryValue(totalBalanceValue)
            }
        }

        viewModelScope.launch {
            balanceHiddenManager.anyTransactionVisibilityChangedFlow.collect {
                refreshTransactionsFromCache()
            }
        }

        viewModelScope.launch {
            amlStatusManager.statusUpdates.collect { update ->
                updateTransactionAmlStatus(update.uid, update.status)
            }
        }

        viewModelScope.launch {
            amlStatusManager.enabledStateFlow.collect { enabled ->
                if (enabled) {
                    // Trigger AML checks for currently loaded transactions
                    transactions?.values?.flatten()?.forEach { viewItem ->
                        fetchAmlStatusIfNeeded(viewItem.uid)
                    }
                } else {
                    // Remove AML status from all transactions
                    transactions = transactions?.withClearedAmlStatus()
                }
                emitState()
            }
        }

        viewModelScope.launch {
            val adapter = adapterManager.awaitAdapterForWallet<IReceiveAdapter>(wallet, ADAPTER_AWAIT_TIMEOUT_MS)
            if (adapter != null) {
                swapProviderTransactionsStorage.observeByToken(
                    token = wallet.token,
                    address = adapter.receiveAddress
                ).collect { swaps ->
                    swapStatusMap = swaps.toRecordUidMap()
                    refreshTransactionsFromCache()
                }
            }
        }

        totalBalance.start(viewModelScope)
    }

    private fun updateSecondaryValue(totalBalanceValue: TotalService.State = totalBalance.stateFlow.value) {
        val oldBalanceViewItem = balanceViewItem ?: return
        val fallbackValue = oldBalanceViewItem.secondaryValue.value

        val updatedValue = when (totalBalanceValue) {
            is TotalService.State.Visible -> {
                if (showCurrencyAsSecondary) {
                    totalBalanceValue.currencyValue?.getFormattedFull() ?: fallbackValue
                } else {
                    totalBalanceValue.coinValue?.getFormattedFull()
                        ?: secondaryValue.value.ifEmpty { fallbackValue }
                }
            }

            TotalService.State.Hidden -> fallbackValue
        }

        secondaryValue = oldBalanceViewItem.secondaryValue.copy(value = updatedValue)
    }

    private suspend fun isSwappable(token: Token) =
        App.instance.isSwapEnabled && (
                token.swappable ||
                        getChangeNowAssociatedCoinTickerUseCase(
                            token.coin.uid,
                            token.blockchainType.uid
                        ) != null)

    fun showAllTransactions(show: Boolean) = transactionHiddenManager.showAllTransactions(show)

    private fun refreshTransactionsFromCache() {
        val currentItems = transactionsService.transactionItemsFlow.value
        if (currentItems.isNotEmpty()) {
            updateTransactions(currentItems)
        }
    }

    fun startStatusChecker() {
        statusCheckerJob?.cancel()
        statusCheckerJob = viewModelScope.launch {
            while (isActive) {
                adapterManager.getReceiveAdapterForWallet(wallet)?.let { adapter ->
                    updateSwapProviderTransactionsStatusUseCase(wallet.token, adapter.receiveAddress)
                }
                delay(30_000)
            }
        }
    }

    fun stopStatusChecker() {
        statusCheckerJob?.cancel()
    }

    private fun shouldShowAmlPromo(): Boolean {
        val hasTransactions = transactions?.values?.flatten()?.isNotEmpty() == true
        return amlPromoAlertEnabled && hasTransactions
    }

    override fun createState() = TokenBalanceUiState(
        title = title,
        balanceViewItem = balanceViewItem,
        transactions = transactions,
        hasHiddenTransactions = hasHiddenTransactions,
        showAmlPromo = shouldShowAmlPromo(),
        amlCheckEnabled = amlStatusManager.isEnabled
    )

    private fun updateTransactions(items: List<TransactionItem>) {
        transactions =
            if (transactionHiddenManager.transactionHiddenFlow.value.transactionHidden) {
                when (transactionHiddenManager.transactionHiddenFlow.value.transactionDisplayLevel) {
                    TransactionDisplayLevel.NOTHING -> emptyList()
                    TransactionDisplayLevel.LAST_1_TRANSACTION -> items.take(1)
                    TransactionDisplayLevel.LAST_2_TRANSACTIONS -> items.take(2)
                    TransactionDisplayLevel.LAST_4_TRANSACTIONS -> items.take(4)
                }.also { hasHiddenTransactions = items.size != it.size }
            } else {
                items.also { hasHiddenTransactions = false }
            }.distinctBy { it.record.uid }
                .map { item ->
                    val matchedSwap = swapStatusMap[item.record.uid]
                    transactionViewItem2Factory.convertToViewItemCached(
                        transactionItem = item,
                        walletUid = wallet.tokenQueryId,
                        matchedSwap = matchedSwap
                    )
                }
                .map { amlStatusManager.applyStatus(it) }
                .groupBy { it.formattedDate }

        emitState()
    }

    private fun updateBalanceViewItem(balanceItem: BalanceItem, isSwappable: Boolean) {
        val balanceViewItem = balanceViewItemFactory.viewItem(
            item = balanceItem,
            currency = balanceService.baseCurrency,
            hideBalance = balanceHiddenManager.isWalletBalanceHidden(wallet.tokenQueryId),
            watchAccount = wallet.account.isWatchAccount,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = isSwappable
        )

        this.balanceViewItem = balanceViewItem.copy(
            primaryValue = balanceViewItem.primaryValue.copy(value = balanceViewItem.primaryValue.value + " " + balanceViewItem.wallet.coin.code)
        )

        totalBalance.setTotalServiceItems(
            listOf(
                TotalService.BalanceItem(
                    value = balanceItem.balanceData.total,
                    coinPrice = balanceItem.coinPrice,
                    isValuePending = false
                )
            )
        )

        updateSecondaryValue()
        emitState()
    }

    @Throws(BackupRequiredError::class, IllegalStateException::class)
    fun getWalletForReceive(): Wallet {
        val account =
            accountManager.activeAccount ?: throw IllegalStateException("Active account is not set")
        when {
            account.hasAnyBackup || !wallet.account.supportsBackup -> return wallet
            else -> throw BackupRequiredError(account, wallet.coin.name)
        }
    }

    fun onBottomReached() {
        transactionsService.loadNext()
    }

    fun willShow(viewItem: TransactionViewItem) {
        transactionsService.fetchRateIfNeeded(viewItem.uid)
        fetchAmlStatusIfNeeded(viewItem.uid)
    }

    private fun fetchAmlStatusIfNeeded(uid: String) {
        val transactionItem = transactionsService.getTransactionItem(uid) ?: return
        amlStatusManager.fetchStatusIfNeeded(uid, transactionItem.record)
    }

    private fun updateTransactionAmlStatus(uid: String, status: AmlStatus?) {
        transactions?.let {
            transactions = it.withUpdatedAmlStatus(uid, status)
            emitState()
        }
    }

    fun getTransactionItem(viewItem: TransactionViewItem) =
        transactionsService.getTransactionItem(viewItem.uid)?.copy(
            transactionStatusUrl = viewItem.transactionStatusUrl,
            changeNowTransactionId = viewItem.changeNowTransactionId,
            walletUid = wallet.tokenQueryId
        )

    fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleWalletBalanceHidden(wallet.tokenQueryId)
    }

    fun toggleTotalType() {
        val currentSecondaryToken = totalBalance.stateFlow.value as? TotalService.State.Visible
        if (showCurrencyAsSecondary) {
            showCurrencyAsSecondary = false
            if (currentSecondaryToken?.coinValue?.coin?.uid == wallet.coin.uid) {
                totalBalance.toggleTotalType()
            } else {
                updateSecondaryValue()
            }
            return
        } else if (currentSecondaryToken?.coinValue?.coin?.uid == BlockchainType.Bitcoin.uid) {
            showCurrencyAsSecondary = true
            updateSecondaryValue()
        }
        totalBalance.toggleTotalType()
    }

    fun getSyncErrorDetails(viewItem: BalanceViewItem): BalanceViewModel.SyncError = when {
        connectivityManager.isConnected.value -> BalanceViewModel.SyncError.Dialog(
            viewItem.wallet,
            viewItem.errorMessage
        )

        else -> BalanceViewModel.SyncError.NetworkNotAvailable()
    }

    fun proposeShielding() {
        val logger = logger.getScopedUnique()
        viewModelScope.launch {
            try {
                sendResult = SendResult.Sending
                (adapterManager.getAdapterForWalletOld(wallet) as? ZcashAdapter?)?.let { adapter ->
                    adapter.proposeShielding()
                }
                sendResult = SendResult.Sent()
            } catch (e: Throwable) {
                logger.warning("failed", e)
                sendResult = SendResult.Failed(SendZCashViewModel.createCaution(e))
            }
            delay(1000)
            sendResult = null
        }
    }

    fun refresh() = viewModelScope.launch {
        refreshing = true
        balanceService.refreshRates()

        adapterManager.refreshByWallet(wallet)
        delay(1000) // to show refresh indicator because `refreshByWallet` works asynchronously
        refreshing = false
    }

    override fun onCleared() {
        super.onCleared()
        balanceService.clear()
        totalBalance.stop()
    }

    fun setAmlCheckEnabled(enabled: Boolean) {
        amlStatusManager.setEnabled(enabled)
    }

    fun dismissAmlPromo() {
        premiumSettings.setAmlCheckShowAlert(false)
        amlPromoAlertEnabled = false
        emitState()
    }
}

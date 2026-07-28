package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.isCustom
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.MarketFavoritesManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.storage.toRecordUidMap
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.balance.BackupRequiredError
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.balance.token.TokenBalanceModule.StakingStatus
import cash.p.terminal.modules.balance.token.TokenBalanceModule.TokenBalanceUiState
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.fee.getWarningThreshold
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.SearchScanState
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionSearchController
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.modules.transactions.isVisibleFor
import cash.p.terminal.modules.transactions.requestNextPageIfAllFilteredOut
import cash.p.terminal.modules.transactions.withClearedAmlStatus
import cash.p.terminal.modules.transactions.withUpdatedAmlStatus
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.canSwap
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.isBackedUpOrNotRequired
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.hoursUntil
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.toHexReversed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

@Suppress("LongParameterList")
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
    private val premiumSettings: PremiumSettings,
    private val amlStatusManager: AmlStatusManager,
    private val marketFavoritesManager: MarketFavoritesManager,
    private val stackingManager: StackingManager,
    private val priceManager: PriceManager,
    private val localStorage: ILocalStorage,
    private val numberFormatter: IAppNumberFormatter,
    private val contactsRepository: ContactsRepository,
) : ViewModelUiState<TokenBalanceUiState>(), TransactionSearchController.Host {

    private val logger = AppLogger("TokenBalanceViewModel-${wallet.coin.code}")
    private val stackingType: StackingType? = when {
        wallet.isPirateCash() -> StackingType.PCASH
        wallet.isCosanta() -> StackingType.COSANTA
        else -> null
    }
    private val isStakingCoin = stackingType != null
    private val updateSwapProviderTransactionsStatusUseCase: UpdateSwapProviderTransactionsStatusUseCase =
        getKoinInstance()
    private val adapterManager: IAdapterManager = getKoinInstance()
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage = getKoinInstance()
    private val marketKit: MarketKitWrapper = getKoinInstance()
    private val poisonAddressManager: PoisonAddressManager = getKoinInstance()
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository = getKoinInstance()

    private val title = wallet.token.coin.name

    private val searchController = TransactionSearchController(viewModelScope, this)
    private var appliedSearchQuery = ""
    private var searchScanning = false

    private var balanceViewItem: BalanceViewItem? = null
    private var transactions: Map<String, List<TransactionViewItem>>? = null
    private var syncing: Boolean =
        transactionsService.syncingFlow.value || !transactionsService.recordsLoadedFlow.value
    private var hasHiddenTransactions: Boolean = false
    private var amlPromoAlertEnabled = premiumSettings.getAmlCheckShowAlert()
    // Reflects whether the wallet has transactions, updated only on non-search loads. This keeps
    // the AML promo banner from blinking out (and shifting the pinned search panel) on every
    // keystroke, since a search transiently empties the transaction list while scanning.
    private var walletHasTransactions = false
    private var lastAddressPoisoningViewMode = localStorage.addressPoisoningViewMode

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
    private var isFavorite = marketFavoritesManager.isCoinInFavorites(wallet.coin.uid)
    private var stakingStatus: StakingStatus? = null
    private var stakingUnpaid: String? = null
    private var nextAccrualAt: Instant? = null
    private var stakingAddress: String? = null

    private var displayDiffPricePeriod = localStorage.displayDiffPricePeriod
    private var displayDiffOptionType = localStorage.displayDiffOptionType
    private var isRoundingAmount = localStorage.isRoundingAmountMainPage
    private var transactionFiltersEnabled = localStorage.transactionFiltersEnabled
    private var selectedTransactionType = FilterTransactionType.All
    private var hasReachedSynced = false
    private var networkFeeWarning: TokenBalanceModule.NetworkFeeWarningBannerData? = null
    private var networkFeeWarningDismissed =
        localStorage.isNetworkFeeWarningDismissed(wallet.token.blockchainType.uid)

    init {
        viewModelScope.launch {
            balanceService.start()
            transactionsService.start()
            if (isStakingCoin) {
                stakingAddress = adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress
                refreshStaking()
            }

            balanceService.balanceItemFlow.collect { balanceItem ->
                balanceItem?.let {
                    updateNetworkFeeWarning()
                    updateBalanceViewItem(
                        balanceItem = it,
                        isSwappable = isSwappable()
                    )
                    if (isStakingCoin) {
                        checkStakingStatus(it)
                        refreshStaking()
                    }
                }
            }
        }

        viewModelScope.launch {
            balanceHiddenManager.walletBalanceHiddenFlow(wallet.tokenQueryId).collect {
                balanceService.balanceItem?.let {
                    updateBalanceViewItem(
                        balanceItem = it,
                        isSwappable = isSwappable()
                    )
                    transactionViewItem2Factory.updateCache()
                    transactionsService.refreshList()
                }
            }
        }

        viewModelScope.launch {
            nativeBalanceProvider?.nativeBalanceUpdatedFlow?.collect {
                if (balanceService.balanceItem == null) return@collect
                updateNetworkFeeWarning()
                emitState()
            }
        }

        viewModelScope.launch {
            merge(
                priceManager.displayPricePeriodFlow.map {},
                priceManager.displayDiffOptionTypeFlow.map {},
            ).collect {
                val newPeriod = priceManager.displayPricePeriod
                val newOptionType = priceManager.displayDiffOptionType
                if (newPeriod == displayDiffPricePeriod && newOptionType == displayDiffOptionType) return@collect
                displayDiffPricePeriod = newPeriod
                displayDiffOptionType = newOptionType
                balanceService.balanceItem?.let {
                    updateBalanceViewItem(
                        balanceItem = it,
                        isSwappable = isSwappable()
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(transactionsService.transactionItemsFlow, transactionsService.searchScanStateFlow, ::updateTransactions)
                .collect { }
        }

        viewModelScope.launch {
            combine(
                transactionsService.syncingFlow,
                transactionsService.recordsLoadedFlow
            ) { syncing, recordsLoaded ->
                syncing || !recordsLoaded
            }.collect { newSyncing ->
                val wasSyncing = syncing
                syncing = newSyncing
                if (wasSyncing && !newSyncing && transactions == null) {
                    updateTransactions(transactionsService.transactionItemsFlow.value, transactionsService.searchScanStateFlow.value)
                }
                emitState()
            }
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
            contactsRepository.contactsFlow.collect {
                transactionViewItem2Factory.clearCache()
                refreshTransactionsFromCache()
            }
        }

        viewModelScope.launch {
            poisonAddressManager.poisonDbChangedFlow.collect {
                transactionViewItem2Factory.clearCache()
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
            swapProviderTransactionsStorage.observeAllByAccount(wallet.account.id)
                .collect { swaps ->
                    swapStatusMap = swaps.toRecordUidMap()
                    refreshTransactionsFromCache()
                }
        }

        totalBalance.start(viewModelScope)

        if (isStakingCoin) {
            viewModelScope.launch {
                stackingManager.infoFlow(wallet).collect { info ->
                    stakingUnpaid = info?.unpaid?.let { value ->
                        if (value > BigDecimal.ZERO) {
                            numberFormatter.formatCoinShort(value, wallet.coin.code, wallet.decimal)
                        } else null
                    }
                    nextAccrualAt = info?.nextAccrualAt
                    emitState()
                }
            }
        }
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

    private fun isSwappable() =
        App.instance.isSwapEnabled && wallet.account.canSwap()

    fun showAllTransactions(show: Boolean) = transactionHiddenManager.showAllTransactions(show)

    fun onSearchClick() = searchController.onSearchClick()
    fun onSearchQueryChange(query: String) = searchController.onSearchQueryChange(query)
    fun onSearchClose() = searchController.onSearchClose()

    override fun onSearchStateChanged() = emitState()

    override suspend fun applySearchQuery(query: String) {
        if (appliedSearchQuery == query) return

        appliedSearchQuery = query
        transactions = if (appliedSearchQuery.isEmpty()) null else emptyMap()
        searchScanning = appliedSearchQuery.isNotEmpty()
        // Reopen the "please wait" window (see updateTransactions()) so the reload to the full list can't flash empty.
        if (appliedSearchQuery.isEmpty()) syncing = true
        emitState()
        transactionsService.setSearchQuery(appliedSearchQuery.ifBlank { null })
    }

    private suspend fun refreshTransactionsFromCache() {
        val currentItems = transactionsService.transactionItemsFlow.value
        if (currentItems.isNotEmpty()) {
            updateTransactions(currentItems, transactionsService.searchScanStateFlow.value)
        }
    }

    fun refreshTransactionDisplaySettings() {
        val current = localStorage.addressPoisoningViewMode
        if (current != lastAddressPoisoningViewMode) {
            lastAddressPoisoningViewMode = current
            transactionViewItem2Factory.updateCache()
            viewModelScope.launch {
                refreshTransactionsFromCache()
            }
        }
    }

    fun startStatusChecker() {
        statusCheckerJob?.cancel()
        statusCheckerJob = viewModelScope.launch {
            while (isActive) {
                updateSwapProviderTransactionsStatusUseCase(wallet.account.id)
                delay(30_000)
            }
        }
    }

    fun stopStatusChecker() {
        statusCheckerJob?.cancel()
    }

    private fun shouldShowAmlPromo(): Boolean {
        return amlPromoAlertEnabled && walletHasTransactions
    }

    private fun refreshStaking() {
        val address = stakingAddress ?: return
        val rawBalance = adapterManager.getBalanceAdapterForWallet(wallet)
            ?.balanceData?.available
        stackingManager.loadInvestmentData(wallet, address, rawBalance)
    }

    private fun checkStakingStatus(balanceItem: BalanceItem) {
        val type = stackingType ?: return
        val threshold = BigDecimal(type.minStackingAmount)
        val balance = balanceItem.balanceData.total

        stakingStatus = if (balance >= threshold) StakingStatus.ACTIVE else StakingStatus.INACTIVE
        emitState()
    }

    override fun createState() = TokenBalanceUiState(
        title = title,
        coinCode = wallet.token.coin.code,
        badge = wallet.token.badge,
        balanceViewItem = balanceViewItem,
        transactions = transactions,
        hasHiddenTransactions = hasHiddenTransactions,
        showAmlPromo = shouldShowAmlPromo(),
        amlCheckEnabled = amlStatusManager.isEnabled,
        isFavorite = isFavorite,
        stakingStatus = stakingStatus,
        stackingType = stackingType,
        stakingUnpaid = stakingUnpaid,
        hoursUntilNextAccrual = calculateHoursUntilNextAccrual(),
        isCustomToken = wallet.token.isCustom,
        displayDiffPricePeriod = displayDiffPricePeriod,
        displayDiffOptionType = displayDiffOptionType,
        isRoundingAmount = isRoundingAmount,
        isShowShieldFunds = isShowShieldFunds(),
        zcashMigrationRequiredAmount = zcashMigrationRequiredAmount(),
        networkFeeWarning = networkFeeWarning,
        syncing = syncing,
        transactionFiltersEnabled = transactionFiltersEnabled,
        transactionFilterTypes = if (transactionFiltersEnabled) {
            FilterTransactionType.entries.map { Filter(it, it == selectedTransactionType) }
        } else emptyList(),
        searchActive = searchController.searchActive,
        searchQuery = searchController.searchQuery,
        searchScanning = searchScanning,
        searchEmptyResult = appliedSearchQuery.isNotEmpty() && !searchScanning && transactions?.values?.flatten().isNullOrEmpty(),
    )

    private fun calculateHoursUntilNextAccrual(): Int? {
        if (stackingType == null || stakingStatus != StakingStatus.ACTIVE) return null
        return nextAccrualAt?.hoursUntil()
    }

    private val nativeBalanceProvider: INativeBalanceProvider?
        get() = adapterManager.getBalanceAdapterForWallet(wallet) as? INativeBalanceProvider

    private fun updateNetworkFeeWarning() {
        if (
            wallet.token.type.isNative ||
            networkFeeWarningDismissed ||
            !hasReachedSyncedState()
        ) {
            networkFeeWarning = null
            return
        }
        val blockchainType = wallet.token.blockchainType
        val nativeToken = marketKit.token(
            TokenQuery(blockchainType, TokenType.Native)
        ) ?: return
        val nativeBalance = nativeBalanceProvider?.nativeBalanceData?.total ?: BigDecimal.ZERO
        val threshold = getWarningThreshold(blockchainType)
        val hasEnoughBalance = if (threshold != null) {
            nativeBalance >= threshold
        } else {
            nativeBalance > BigDecimal.ZERO
        }
        if (hasEnoughBalance) {
            networkFeeWarning = null
            return
        }
        val blockchainName = marketKit.blockchain(blockchainType.uid)?.name ?: blockchainType.uid
        val nativeCoinCode = nativeToken.coin.code
        val tokenName = wallet.token.coin.code
        val formattedBalance = numberFormatter.formatCoinShort(
            nativeBalance, nativeCoinCode, nativeToken.decimals
        )
        val context = App.instance
        networkFeeWarning = TokenBalanceModule.NetworkFeeWarningBannerData(
            title = context.getString(
                R.string.token_balance_need_native_coin_title,
                nativeCoinCode,
                tokenName
            ),
            body = context.getString(
                R.string.token_balance_need_native_coin_body,
                nativeCoinCode,
                tokenName,
                blockchainName,
                formattedBalance
            ),
            formattedBalance = formattedBalance
        )
    }

    fun dismissNetworkFeeWarning() {
        localStorage.dismissNetworkFeeWarning(wallet.token.blockchainType.uid)
        networkFeeWarningDismissed = true
        networkFeeWarning = null
        emitState()
    }

    private fun hasReachedSyncedState(): Boolean {
        val item = balanceService.balanceItem ?: return hasReachedSynced
        if (item.state is AdapterState.Synced) {
            hasReachedSynced = true
        }
        return hasReachedSynced
    }

    private fun isShowShieldFunds(): Boolean {
        val item = balanceService.balanceItem ?: return hasReachedSynced
        val isTransparent =
            (item.wallet.token.type as? TokenType.AddressSpecTyped)?.type == TokenType.AddressSpecType.Transparent
        if (!isTransparent || item.balanceData.available <= ZcashAdapter.MINERS_FEE) return false

        return hasReachedSyncedState()
    }

    private fun zcashMigrationRequiredAmount(): String? {
        // The typed lookup is an unchecked cast, so it must not be reached for other blockchains.
        if (wallet.token.blockchainType != BlockchainType.Zcash) return null
        // Same prerequisite as the offer on the balance screen: migrating costs a fee and moves
        // the whole pool, so it must not be reachable before the account can be recovered.
        if (!wallet.account.isBackedUpOrNotRequired()) return null

        return adapterManager.getAdapterForWallet<ZcashAdapter>(wallet)
            ?.ironwoodMigrationRequiredBalance
            ?.let { numberFormatter.formatCoinShort(it, wallet.coin.code, wallet.decimal) }
    }

    private suspend fun updateTransactions(items: List<TransactionItem>, scanState: SearchScanState) {
        // While a search scan runs, show only the spinner - a search batch has a single terminal (Finished) emission.
        if (appliedSearchQuery.isNotEmpty() && scanState == SearchScanState.Scanning) {
            transactions = emptyMap()
            searchScanning = true
            emitState()
            return
        }
        searchScanning = false

        // Skip the initial empty emission from transactionRecordRepository.set() while
        // still syncing. Once syncing finishes, allow empty items through so coins with
        // zero transactions show "no transactions" instead of "wait for sync" forever.
        if (items.isEmpty() && transactions == null && syncing) return

        // Filter swaps out before the privacy truncation, so the "last N" limit counts only
        // visible transfers - otherwise the newest swaps would consume the quota and hide real ones.
        val visibleViewItems = items
            .distinctBy { it.record.uid }
            .map { item ->
                val matchedSwap = swapStatusMap[item.record.uid]
                transactionViewItem2Factory.convertToViewItemCached(
                    transactionItem = item,
                    walletUid = wallet.tokenQueryId,
                    matchedSwap = matchedSwap
                )
            }
            .filter { it.isVisibleFor(selectedTransactionType) }
            .map { amlStatusManager.applyStatus(it) }

        val hiddenState = transactionHiddenManager.transactionHiddenFlow.value
        transactions = if (hiddenState.transactionHidden) {
            when (hiddenState.transactionDisplayLevel) {
                TransactionDisplayLevel.NOTHING -> emptyList()
                TransactionDisplayLevel.LAST_1_TRANSACTION -> visibleViewItems.take(1)
                TransactionDisplayLevel.LAST_2_TRANSACTIONS -> visibleViewItems.take(2)
                TransactionDisplayLevel.LAST_4_TRANSACTIONS -> visibleViewItems.take(4)
            }.also { hasHiddenTransactions = visibleViewItems.size != it.size }
        } else {
            visibleViewItems.also { hasHiddenTransactions = false }
        }.groupBy { it.formattedDate }

        // Only a non-search load reflects the real wallet history; a search filters the list and
        // must not flip the AML promo banner (see walletHasTransactions).
        if (appliedSearchQuery.isEmpty()) {
            walletHasTransactions = transactions?.values?.flatten()?.isNotEmpty() == true
            // A page whose transfers are all swaps filters to nothing; with no visible row the list
            // can't reach its bottom to page further, so request the next page until a visible row
            // appears or the source is exhausted (loadNext is a no-op once exhausted).
            requestNextPageIfAllFilteredOut(items.size, visibleViewItems.size, transactionsService::loadNext)
        }

        emitState()
    }

    private fun updateBalanceViewItem(balanceItem: BalanceItem, isSwappable: Boolean) {
        val balanceViewItem = balanceViewItemFactory.viewItem(
            item = balanceItem,
            currency = balanceService.baseCurrency,
            hideBalance = balanceHiddenManager.isWalletBalanceHidden(wallet.tokenQueryId),
            watchAccount = wallet.account.isWatchAccount,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = isSwappable,
            displayDiffOptionType = priceManager.displayDiffOptionType,
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
            account.isBackedUpOrNotRequired() -> return wallet
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

    fun toggleFavorite() {
        val coinUid = wallet.coin.uid
        if (isFavorite) {
            marketFavoritesManager.remove(coinUid)
        } else {
            marketFavoritesManager.add(coinUid)
        }
        isFavorite = !isFavorite
        emitState()
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
                val zcashAdapter = adapterManager.getAdapterForWallet<ZcashAdapter>(wallet)
                val txHash = zcashAdapter?.proposeShielding()?.byteArray?.toHexReversed()
                txHash?.let {
                    locallyCreatedTransactionRepository.markCreated(wallet, it)
                }
                sendResult = SendResult.Sent(txHash)
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

    fun onResume() {
        if (!isStakingCoin) return
        viewModelScope.launch {
            val address = stakingAddress
                ?: adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress
                ?: return@launch
            stakingAddress = address
            val balance = adapterManager.getBalanceAdapterForWallet(wallet)
                ?.balanceData?.available
            stackingManager.loadInvestmentData(wallet, address, balance)
        }
    }

    override fun onCleared() {
        super.onCleared()
        balanceService.clear()
        transactionsService.clear()
        totalBalance.stop()
    }

    fun setAmlCheckEnabled(enabled: Boolean) {
        amlStatusManager.setEnabled(enabled)
    }

    fun setDisplayPricePeriod(period: DisplayPricePeriod) {
        localStorage.displayDiffPricePeriod = period
    }

    fun setDisplayDiffOptionType(type: DisplayDiffOptionType) {
        localStorage.displayDiffOptionType = type
    }

    fun setRoundingAmount(enabled: Boolean) {
        localStorage.isRoundingAmountMainPage = enabled
        isRoundingAmount = enabled
        emitState()
    }

    fun setTransactionFiltersEnabled(enabled: Boolean) {
        localStorage.transactionFiltersEnabled = enabled
        transactionFiltersEnabled = enabled
        // Reset to All when turning off so the user isn't stuck on a filtered list with no tabs.
        if (!enabled && selectedTransactionType != FilterTransactionType.All) {
            setTransactionType(FilterTransactionType.All)
        } else {
            emitState()
        }
    }

    fun setTransactionType(type: FilterTransactionType) {
        if (type == selectedTransactionType) return
        selectedTransactionType = type
        // Open a loading window for the switch: keep transactions null and syncing true so the
        // screen shows "please wait" instead of flashing "no transactions". The updateTransactions
        // guard relies on syncing, and the syncing-finished hook relies on transactions == null,
        // until the new filter's first batch arrives.
        transactions = null
        syncing = true
        // A non-search filter switch hides the promo during its loading window, exactly as before;
        // updateTransactions recomputes walletHasTransactions once the new filter's list arrives.
        if (appliedSearchQuery.isEmpty()) {
            walletHasTransactions = false
        }
        transactionsService.setTransactionType(type)
        emitState()
    }

    fun dismissAmlPromo() {
        premiumSettings.setAmlCheckShowAlert(false)
        amlPromoAlertEnabled = false
        emitState()
    }
}

package cash.p.terminal.modules.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.MutableLiveData
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.Calendar
import java.util.Date

class TransactionsViewModel(
    private val service: TransactionsService,
    private val transactionViewItem2Factory: TransactionViewItemFactory,
    private val balanceHiddenManager: BalanceHiddenManager,
    private val transactionAdapterManager: TransactionAdapterManager,
    private val walletManager: IWalletManager,
    private val transactionFilterService: TransactionFilterService,
    private val transactionHiddenManager: TransactionHiddenManager,
    private val premiumSettings: PremiumSettings,
    private val amlStatusManager: AmlStatusManager
) : ViewModelUiState<TransactionsUiState>() {

    var tmpItemToShow: TransactionItem? = null

    val filterResetEnabled = MutableLiveData<Boolean>()
    val filterTokensLiveData = MutableLiveData<List<Filter<FilterToken?>>>()
    val filterTypesLiveData = MutableLiveData<List<Filter<FilterTransactionType>>>()
    val filterBlockchainsLiveData = MutableLiveData<List<Filter<Blockchain?>>>()
    val filterContactLiveData = MutableLiveData<Contact?>()
    var filterHideSuspiciousTx = MutableLiveData<Boolean>()

    private var transactionListId: String? = null
    private var transactions: Map<String, List<TransactionViewItem>>? = null
    private var viewState: ViewState = ViewState.Loading
    private var syncing = false
    private var hasHiddenTransactions: Boolean = false
    private var filterVersion = 0
    private var currentFilterType: FilterTransactionType = FilterTransactionType.All
    private var amlPromoAlertEnabled = premiumSettings.getAmlCheckShowAlert()

    val balanceHidden: Boolean
        get() = balanceHiddenManager.balanceHidden

    fun toggleTransactionInfoHidden(transactionId: String) =
        balanceHiddenManager.toggleTransactionInfoHidden(transactionId)

    private var refreshViewItemsJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            service.start()
        }

        viewModelScope.launch(Dispatchers.Default) {
            transactionAdapterManager.adaptersReadyFlow.collect {
                handleUpdatedWallets(walletManager.activeWallets)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            transactionFilterService.stateFlow.collect { state ->
                val transactionWallets = state.filterTokens.map { filterToken ->
                    filterToken?.let {
                        TransactionWallet(it.token, it.source, it.token.badge)
                    }
                }
                val selectedTransactionWallet = state.selectedToken?.let {
                    TransactionWallet(it.token, it.source, it.token.badge)
                }

                val newTransactionListId = (selectedTransactionWallet?.hashCode() ?: 0).toString() +
                        state.selectedTransactionType.name +
                        state.selectedBlockchain?.uid

                // If filter changed, reset state to show loading and increment version
                if (transactionListId != newTransactionListId) {
                    transactionListId = newTransactionListId
                    filterVersion++
                    viewState = ViewState.Loading
                    transactions = null
                    emitState()
                }

                service.set(
                    transactionWallets.filterNotNull(),
                    selectedTransactionWallet,
                    state.selectedTransactionType,
                    state.selectedBlockchain,
                    state.contact,
                )

                filterResetEnabled.postValue(state.resetEnabled)

                val types = state.transactionTypes
                val selectedType = state.selectedTransactionType
                currentFilterType = selectedType
                val filterTypes = types.map { Filter(it, it == selectedType) }
                filterTypesLiveData.postValue(filterTypes)

                val blockchains = state.blockchains
                val selectedBlockchain = state.selectedBlockchain
                val filterBlockchains = blockchains.map { Filter(it, it == selectedBlockchain) }
                filterBlockchainsLiveData.postValue(filterBlockchains)

                val filterCoins = state.filterTokens.map {
                    Filter(it, it == state.selectedToken)
                }
                filterTokensLiveData.postValue(filterCoins)

                filterContactLiveData.postValue(state.contact)

                if (filterHideSuspiciousTx.value != state.hideSuspiciousTx) {
                    service.reload()
                }
                filterHideSuspiciousTx.postValue(state.hideSuspiciousTx)
            }
        }

        viewModelScope.launch {
            service.syncingObservable.asFlow().collect {
                syncing = it
                emitState()
            }
        }

        viewModelScope.launch {
            service.itemsObservable.asFlow().collect { items ->
                handleUpdatedItems(items.distinctBy { it.record.uid })
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            balanceHiddenManager.balanceHiddenFlow.collect {
                transactionViewItem2Factory.updateCache()
                service.refreshList()
            }
        }

        viewModelScope.launch {
            balanceHiddenManager.anyTransactionVisibilityChangedFlow.collect {
                service.refreshList()
            }
        }

        viewModelScope.launch {
            transactionHiddenManager.transactionHiddenFlow.collectLatest {
                service.reload()
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
    }

    fun showAllTransactions(show: Boolean) = transactionHiddenManager.showAllTransactions(show)

    private fun handleUpdatedItems(items: List<TransactionItem>) {
        refreshViewItemsJob?.cancel()
        refreshViewItemsJob = viewModelScope.launch(Dispatchers.Default) {
            // Capture current filter version to detect if filter changes during processing
            val capturedFilterVersion = filterVersion

            val viewItems =
                if (transactionHiddenManager.transactionHiddenFlow.value.transactionHidden) {
                    when (transactionHiddenManager.transactionHiddenFlow.value.transactionDisplayLevel) {
                        TransactionDisplayLevel.NOTHING -> emptyList()
                        TransactionDisplayLevel.LAST_1_TRANSACTION -> items.take(1)
                        TransactionDisplayLevel.LAST_2_TRANSACTIONS -> items.take(2)
                        TransactionDisplayLevel.LAST_4_TRANSACTIONS -> items.take(4)
                    }.also { hasHiddenTransactions = items.size != it.size }
                } else {
                    items.also { hasHiddenTransactions = false }
                }.map {
                    ensureActive()
                    transactionViewItem2Factory.convertToViewItemCached(it)
                        .let { viewItem -> amlStatusManager.applyStatus(viewItem) }
                }.groupBy {
                    ensureActive()
                    it.formattedDate
                }

            ensureActive()

            // Only update state if filter version hasn't changed (ignore stale data)
            if (capturedFilterVersion == filterVersion) {
                transactions = viewItems
                viewState = ViewState.Success
                emitState()
            }
        }
    }

    private fun shouldShowAmlPromo(): Boolean {
        val hasTransactions = transactions?.values?.flatten()?.isNotEmpty() == true
        val isValidFilter = currentFilterType == FilterTransactionType.All ||
                currentFilterType == FilterTransactionType.Incoming
        return amlPromoAlertEnabled && hasTransactions && isValidFilter
    }

    override fun createState() = TransactionsUiState(
        transactions = transactions,
        viewState = viewState,
        transactionListId = transactionListId,
        syncing = syncing,
        hasHiddenTransactions = hasHiddenTransactions,
        showAmlPromo = shouldShowAmlPromo(),
        amlCheckEnabled = amlStatusManager.isEnabled
    )

    private fun handleUpdatedWallets(wallets: List<Wallet>) {
        transactionFilterService.setWallets(wallets)
    }

    fun setFilterTransactionType(filterType: FilterTransactionType) {
        transactionFilterService.setSelectedTransactionType(filterType)
    }

    fun setFilterToken(w: FilterToken?) {
        transactionFilterService.setSelectedToken(w)
    }

    fun onEnterFilterBlockchain(filterBlockchain: Filter<Blockchain?>) {
        transactionFilterService.setSelectedBlockchain(filterBlockchain.item)
    }

    fun onEnterContact(contact: Contact?) {
        transactionFilterService.setContact(contact)
    }

    fun resetFilters() {
        transactionFilterService.reset()
    }

    fun onBottomReached() {
        service.loadNext()
    }

    fun willShow(viewItem: TransactionViewItem) {
        service.fetchRateIfNeeded(viewItem.uid)
        fetchAmlStatusIfNeeded(viewItem.uid)
    }

    private fun fetchAmlStatusIfNeeded(uid: String) {
        val transactionItem = service.getTransactionItem(uid) ?: return
        amlStatusManager.fetchStatusIfNeeded(uid, transactionItem.record)
    }

    private fun updateTransactionAmlStatus(uid: String, status: AmlStatus?) {
        transactions?.let {
            transactions = it.withUpdatedAmlStatus(uid, status)
            emitState()
        }
    }

    override fun onCleared() {
        service.clear()
    }

    fun getTransactionItem(viewItem: TransactionViewItem) =
        service.getTransactionItem(viewItem.uid)?.copy(
            transactionStatusUrl = viewItem.transactionStatusUrl,
            changeNowTransactionId = viewItem.changeNowTransactionId
        )

    fun updateFilterHideSuspiciousTx(checked: Boolean) {
        transactionFilterService.updateFilterHideSuspiciousTx(checked)
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

data class TransactionItem(
    val record: TransactionRecord,
    val currencyValue: CurrencyValue?,
    val lastBlockInfo: LastBlockInfo?,
    val nftMetadata: Map<NftUid, NftAssetBriefMetadata>,
    val changeNowTransactionId: String? = null,
    val transactionStatusUrl: Pair<String, String>? = null,
    val walletUid: String? = null
) {
    val createdAt = System.currentTimeMillis()
}

@Immutable
data class TransactionViewItem(
    val uid: String,
    val progress: Float?,
    val title: String,
    val subtitle: String,
    val primaryValue: ColoredValue?,
    val secondaryValue: ColoredValue?,
    val date: Date,
    val formattedTime: String,
    val showAmount: Boolean = true,
    val sentToSelf: Boolean = false,
    val doubleSpend: Boolean = false,
    val spam: Boolean = false,
    val locked: Boolean? = null,
    val icon: Icon,
    val changeNowTransactionId: String? = null,
    val transactionStatusUrl: Pair<String, String>? = null,
    val amlStatus: AmlStatus? = null
) {

    sealed class Icon {
        class ImageResource(val resourceId: Int) : Icon()
        class Regular(
            val url: String?,
            val alternativeUrl: String?,
            val placeholder: Int?,
            val rectangle: Boolean = false
        ) : Icon()

        class Double(val back: Regular, val front: Regular) : Icon()
        object Failed : Icon()
        class Platform(blockchainType: BlockchainType) : Icon() {
            val iconRes = when (blockchainType) {
                BlockchainType.BinanceSmartChain -> R.drawable.logo_chain_bsc_trx_24
                BlockchainType.Ethereum -> R.drawable.logo_chain_ethereum_trx_24
                BlockchainType.Polygon -> R.drawable.logo_chain_polygon_trx_24
                BlockchainType.Avalanche -> R.drawable.logo_chain_avalanche_trx_24
                BlockchainType.Optimism -> R.drawable.logo_chain_optimism_trx_24
                BlockchainType.Base -> R.drawable.logo_chain_base_trx_24
                BlockchainType.ZkSync -> R.drawable.logo_chain_zksync_trx_32
                BlockchainType.ArbitrumOne -> R.drawable.logo_chain_arbitrum_one_trx_24
                BlockchainType.Gnosis -> R.drawable.logo_chain_gnosis_trx_32
                BlockchainType.Fantom -> R.drawable.logo_chain_fantom_trx_32
                BlockchainType.Tron -> R.drawable.logo_chain_tron_trx_32
                BlockchainType.Ton -> R.drawable.logo_chain_ton_trx_32
                BlockchainType.Stellar -> R.drawable.logo_chain_stellar_trx_32
                else -> null
            }
        }
    }

    val formattedDate = formatDate(date).uppercase()

    private fun formatDate(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val today = Calendar.getInstance()
        if (calendar[Calendar.YEAR] == today[Calendar.YEAR] && calendar[Calendar.DAY_OF_YEAR] == today[Calendar.DAY_OF_YEAR]) {
            return Translator.getString(R.string.Timestamp_Today)
        }

        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_MONTH, -1)
        if (calendar[Calendar.YEAR] == yesterday[Calendar.YEAR] && calendar[Calendar.DAY_OF_YEAR] == yesterday[Calendar.DAY_OF_YEAR]) {
            return Translator.getString(R.string.Timestamp_Yesterday)
        }

        return DateHelper.shortDate(date, "MMMM d", "MMMM d, yyyy")
    }
}

enum class FilterTransactionType {
    All, Incoming, Outgoing, Swap, Approve;

    val title: Int
        get() = when (this) {
            All -> R.string.Transactions_All
            Incoming -> R.string.Transactions_Incoming
            Outgoing -> R.string.Transactions_Outgoing
            Swap -> R.string.Transactions_Swaps
            Approve -> R.string.Transactions_Approvals
        }
}

enum class AmlStatus {
    Loading,
    Unknown,
    Low,
    Medium,
    High;

    companion object {
        fun from(result: IncomingAddressCheckResult): AmlStatus = when (result) {
            IncomingAddressCheckResult.Unknown -> Unknown
            IncomingAddressCheckResult.Low -> Low
            IncomingAddressCheckResult.Medium -> Medium
            IncomingAddressCheckResult.High -> High
        }
    }
}

val AmlStatus.riskTextRes: Int
    get() = when (this) {
        AmlStatus.Low -> R.string.aml_low_risk
        AmlStatus.Medium -> R.string.aml_medium_risk
        AmlStatus.High -> R.string.aml_high_risk
        AmlStatus.Loading,
        AmlStatus.Unknown -> R.string.aml_unknown
    }

@Composable
fun AmlStatus.riskColor(): Color = when (this) {
    AmlStatus.Low -> ComposeAppTheme.colors.remus
    AmlStatus.Medium -> ComposeAppTheme.colors.jacob
    AmlStatus.High -> ComposeAppTheme.colors.lucian
    AmlStatus.Loading,
    AmlStatus.Unknown -> ComposeAppTheme.colors.grey50
}

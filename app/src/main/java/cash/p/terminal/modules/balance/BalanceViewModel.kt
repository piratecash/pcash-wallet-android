package cash.p.terminal.modules.balance

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.zcash.ZcashAddressValidator
import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.toSeedQrErrorStringRes
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.supported
import cash.p.terminal.core.usecase.PayCoreNavigationTarget
import cash.p.terminal.core.usecase.ResolvePayCoreNavigationUseCase
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.core.utils.AddressUriResult
import cash.p.terminal.core.utils.ToncoinUriParser
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.list.WalletConnectListModule
import cash.p.terminal.modules.walletconnect.list.WalletConnectListViewModel
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.canSwap
import cash.p.terminal.wallet.isBackedUpOrNotRequired
import cash.p.terminal.wallet.isStakingWallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.useCases.WalletUseCase
import com.reown.walletkit.client.Wallet.Params.Pair
import com.reown.walletkit.client.WalletKit
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class BalanceViewModel(
    private val service: DefaultBalanceService,
    private val balanceViewItemFactory: BalanceViewItemFactory,
    private val balanceViewTypeManager: BalanceViewTypeManager,
    private val totalBalance: TotalBalance,
    private val localStorage: ILocalStorage,
    private val wCManager: WCManager,
    private val addressHandlerFactory: AddressHandlerFactory,
    private val priceManager: PriceManager,
    private val balanceHiddenManager: IBalanceHiddenManager
) : ViewModelUiState<BalanceUiState>(), ITotalBalance by totalBalance {

    private var balanceViewType = balanceViewTypeManager.balanceViewTypeFlow.value
    private var viewState: ViewState? = null
    private var balanceViewItems = listOf<BalanceViewItem2>()
    private var isRefreshing = false
    private var showStackingForWatchAccount = false
    private var openSendTokenSelect: OpenSendTokenSelect? = null
    private var openRestoreFromQr: OpenRestoreFromQr? = null
    private var errorMessage: String? = null
    private var balanceTabButtonsEnabled = localStorage.balanceTabButtonsEnabled

    private val accountManager: IAccountManager by inject(IAccountManager::class.java)
    private val coinManager: ICoinManager by inject(ICoinManager::class.java)
    private val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto by inject(SeedPhraseQrCrypto::class.java)
    private val pendingMultiSwapStorage: PendingMultiSwapStorage by inject(PendingMultiSwapStorage::class.java)
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage by inject(SwapProviderTransactionsStorage::class.java)
    private val resolvePayCoreNavigation: ResolvePayCoreNavigationUseCase by inject(ResolvePayCoreNavigationUseCase::class.java)

    private var pendingSwapCount = 0
    private var singlePendingSwapId: String? = null
    private var singlePayCoreSwapDate: Long? = null
    private var singlePayCoreSwapRecordUid: String? = null
    private var payCoreNavigationInFlight = false

    private val _payCoreNavigationEvents = Channel<PayCoreNavigationTarget>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val payCoreNavigationEvents: Flow<PayCoreNavigationTarget> =
        _payCoreNavigationEvents.receiveAsFlow()

    private val sortTypes =
        listOf(BalanceSortType.Value, BalanceSortType.Name, BalanceSortType.PercentGrowth)
    private var sortType = service.sortType

    private var displayDiffPricePeriod = localStorage.displayDiffPricePeriod

    var isSwapEnabled by mutableStateOf(true)
        private set
    var isStackingEnabled by mutableStateOf(true)
        private set

    var connectionResult by mutableStateOf<WalletConnectListViewModel.ConnectionResult?>(null)
        private set

    private var displayDiffOptionType = localStorage.displayDiffOptionType

    private var refreshViewItemsJob: Job? = null

    init {
        addCloseable(service)

        viewModelScope.launch(Dispatchers.Default) {
            accountManager.activeAccountStateFlow.collect {
                setupUI()
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            service.balanceItemsFlow
                .collect { items ->
                    totalBalance.setTotalServiceItems(items.map {
                        TotalService.BalanceItem(
                            it.balanceData.total,
                            it.state !is AdapterState.Synced,
                            it.coinPrice
                        )
                    })
                    detectPirateAndCosanta(items)
                    refreshViewItems(items)
                }
        }

        viewModelScope.launch {
            totalBalance.stateFlow.collect {
                refreshViewItems(service.balanceItemsFlow.value)
            }
        }

        viewModelScope.launch {
            balanceViewTypeManager.balanceViewTypeFlow.collect {
                handleUpdatedBalanceViewType(it)
            }
        }

        viewModelScope.launch {
            localStorage.balanceTabButtonsEnabledFlow.collect {
                balanceTabButtonsEnabled = it
                emitState()
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            merge(
                priceManager.priceChangeIntervalFlow.map {},
                priceManager.displayPricePeriodFlow.map {},
                priceManager.displayDiffOptionTypeFlow.map {},
            ).collect {
                displayDiffPricePeriod = priceManager.displayPricePeriodFlow.value
                displayDiffOptionType = priceManager.displayDiffOptionTypeFlow.value
                refreshViewItems(service.balanceItemsFlow.value)
            }
        }

        // To hide balance for Samsung devices with hide balance feature enabled
        viewModelScope.launch {
            balanceHiddenManager.anyWalletVisibilityChangedFlow.collect {
                refreshViewItems(service.balanceItemsFlow.value)
            }
        }

        viewModelScope.launch {
            combine(
                pendingMultiSwapStorage.observeForActiveAccount(accountManager.activeAccountStateFlow),
                swapProviderTransactionsStorage.observeForActiveAccount(accountManager.activeAccountStateFlow)
            ) { swaps, providerTxs ->
                val payCoreUnfinished =
                    providerTxs.filter { it.provider == SwapProvider.PAYCORE && !it.isFinished() }
                val singlePayCore = payCoreUnfinished.singleOrNull()
                PendingSwapsSnapshot(
                    totalCount = swaps.size + payCoreUnfinished.size,
                    singleMultiSwapId = swaps.singleOrNull()?.id,
                    singlePayCoreDate = singlePayCore?.date,
                    singlePayCoreRecordUid = singlePayCore?.let {
                        it.outgoingRecordUid ?: it.incomingRecordUid
                    },
                )
            }.collect { snapshot ->
                pendingSwapCount = snapshot.totalCount
                singlePendingSwapId = snapshot.singleMultiSwapId
                singlePayCoreSwapDate = snapshot.singlePayCoreDate
                singlePayCoreSwapRecordUid = snapshot.singlePayCoreRecordUid
                emitState()
            }
        }

        service.start()

        totalBalance.start(viewModelScope)
    }

    private fun setupUI() {
        val activeAccount = accountManager.activeAccount
        val isMoneroAccount = activeAccount?.type is AccountType.MnemonicMonero
        isStackingEnabled = !isMoneroAccount
        isSwapEnabled = !isMoneroAccount && App.instance.isSwapEnabled && (activeAccount?.canSwap() == true)
    }

    override fun createState() = BalanceUiState(
        balanceViewItems = balanceViewItems,
        viewState = viewState,
        isRefreshing = isRefreshing,
        headerNote = headerNote(),
        errorMessage = errorMessage,
        openSend = openSendTokenSelect,
        openRestoreFromQr = openRestoreFromQr,
        balanceTabButtonsEnabled = balanceTabButtonsEnabled,
        sortType = sortType,
        sortTypes = sortTypes,
        showStackingForWatchAccount = showStackingForWatchAccount,
        displayDiffOptionType = displayDiffOptionType,
        displayPricePeriod = displayDiffPricePeriod,
        pendingSwapCount = pendingSwapCount,
        singlePendingSwapId = singlePendingSwapId,
        singlePayCoreSwapDate = singlePayCoreSwapDate,
        singlePayCoreSwapLoading = payCoreNavigationInFlight,
    )

    private fun handleUpdatedBalanceViewType(balanceViewType: BalanceViewType) {
        this.balanceViewType = balanceViewType

        service.balanceItemsFlow.value?.let {
            refreshViewItems(it)
        }
    }

    private fun headerNote(): HeaderNote {
        val account = service.account ?: return HeaderNote.None
        val nonRecommendedDismissed =
            localStorage.nonRecommendedAccountAlertDismissedAccounts.contains(account.id)

        return account.headerNote(nonRecommendedDismissed)
    }

    fun onBalanceClick(item: BalanceViewItem2) {
        HudHelper.vibrate(App.instance)
        balanceHiddenManager.toggleWalletBalanceHidden(item.wallet.tokenQueryId)
    }

    fun onSinglePayCoreSwapClick() {
        val date = singlePayCoreSwapDate ?: return
        if (payCoreNavigationInFlight) return
        payCoreNavigationInFlight = true
        emitState()
        viewModelScope.launch {
            try {
                val target = resolvePayCoreNavigation(date, singlePayCoreSwapRecordUid)
                _payCoreNavigationEvents.trySend(target)
            } finally {
                payCoreNavigationInFlight = false
                emitState()
            }
        }
    }

    override fun toggleBalanceVisibility() {
        totalBalance.toggleBalanceVisibility()
    }

    private fun refreshViewItems(balanceItems: List<BalanceItem>?) {
        refreshViewItemsJob?.cancel()
        refreshViewItemsJob = viewModelScope.launch(Dispatchers.Default) {
            if (balanceItems != null) {
                viewState = ViewState.Success
                balanceViewItems = balanceItems.map { balanceItem ->
                    ensureActive()
                    val isHidden = balanceHiddenManager.isWalletBalanceHidden(balanceItem.wallet.tokenQueryId)
                    balanceViewItemFactory.viewItem2(
                        item = balanceItem,
                        currency = service.baseCurrency,
                        roundingAmount = localStorage.isRoundingAmountMainPage,
                        hideBalance = isHidden,
                        watchAccount = service.isWatchAccount,
                        isSwipeToDeleteEnabled = !isSingleWalletAccount(),
                        balanceViewType = balanceViewType,
                        networkAvailable = service.networkAvailable,
                        showStackingUnpaid = true,
                        displayDiffOptionType = displayDiffOptionType
                    )
                }
            } else {
                viewState = null
                balanceViewItems = listOf()
            }

            ensureActive()
            emitState()
        }
    }

    private fun detectPirateAndCosanta(balanceItems: List<BalanceItem>?) {
        showStackingForWatchAccount =
            balanceItems?.any { it.wallet.isStakingWallet() } ?: false
    }

    fun onHandleRoute() {
        connectionResult = null
    }

    override fun onCleared() {
        totalBalance.stop()
    }

    fun onRefresh() {
        if (isRefreshing) {
            return
        }

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("BalanceViewModel", "Error refreshing balance", throwable)
            isRefreshing = false
            emitState()
        }) {
            isRefreshing = true
            emitState()

            service.refresh()
            // A fake 2 seconds 'refresh'
            delay(2300)

            isRefreshing = false
            emitState()
        }
    }

    fun setSortType(sortType: BalanceSortType) {
        this.sortType = sortType
        emitState()

        viewModelScope.launch(Dispatchers.Default) {
            service.sortType = sortType
        }
    }

    fun setDisplayPricePeriod(displayPricePeriod: DisplayPricePeriod) {
        localStorage.displayDiffPricePeriod = displayPricePeriod
    }

    fun onCloseHeaderNote(headerNote: HeaderNote) {
        when (headerNote) {
            HeaderNote.NonRecommendedAccount -> {
                service.account?.let { account ->
                    localStorage.nonRecommendedAccountAlertDismissedAccounts += account.id
                    emitState()
                }
            }

            else -> Unit
        }
    }

    fun disable(viewItem: BalanceViewItem2) {
        viewModelScope.launch {
            service.disable(viewItem.wallet)
        }
    }

    fun getSyncErrorDetails(viewItem: BalanceViewItem2): SyncError = when {
        service.networkAvailable -> SyncError.Dialog(viewItem.wallet, viewItem.errorMessage)
        else -> SyncError.NetworkNotAvailable()
    }

    fun getReceiveAllowedState(): ReceiveAllowedState? {
        val tmpAccount = service.account ?: return null
        return when {
            tmpAccount.isBackedUpOrNotRequired() -> ReceiveAllowedState.Allowed
            else -> ReceiveAllowedState.BackupRequired(tmpAccount)
        }
    }

    private fun isSingleWalletAccount(): Boolean {
        val account = accountManager.activeAccount ?: return false
        return account.type is AccountType.MnemonicMonero
    }

    /***
     * Return wallet if single wallet account (like Monero)
     */
    fun getSingleWalletForReceive(): Wallet? {
        val account = accountManager.activeAccount ?: return null
        return if (account.type is AccountType.MnemonicMonero) {
            coinManager.getToken(TokenQuery(BlockchainType.Monero, TokenType.Native))?.let {
                walletUseCase.getWallet(it)
            }
        } else {
            null
        }
    }

    fun getWalletConnectSupportState(): WCManager.SupportState {
        return wCManager.getWalletConnectSupportState()
    }

    fun handleScannedData(scannedText: String) {
        val wcUriVersion = WalletConnectListModule.getVersionFromUri(scannedText)
        if (wcUriVersion == 2) {
            handleWalletConnectUri(scannedText)
        } else if (scannedText.startsWith("tc://")) {
            viewModelScope.launch {
                App.tonConnectManager.handle(scannedText, false)
            }
        } else if (scannedText.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            handleEncryptedSeedQr(scannedText)
        } else {
            handleAddressData(scannedText)
        }
    }

    private fun handleEncryptedSeedQr(content: String) {
        seedPhraseQrCrypto.decrypt(content)
            .onSuccess { decrypted ->
                openRestoreFromQr = OpenRestoreFromQr(
                    words = decrypted.words,
                    passphrase = decrypted.passphrase,
                    moneroHeight = decrypted.height,
                    language = decrypted.language
                )
                emitState()
            }
            .onFailure { error ->
                errorMessage = Translator.getString(error.toSeedQrErrorStringRes())
                emitState()
            }
    }

    fun onRestoreFromQrOpened() {
        openRestoreFromQr = null
        emitState()
    }

    private fun uri(text: String): AddressUri? {
        var hasPrefix = AddressUriParser.hasUriPrefix(text)
        val address = if (!hasPrefix && isZCashAddress(text)) {
            // parse as zcash
            hasPrefix = true
            "zcash:$text"
        } else {
            text
        }
        if (hasPrefix) {
            val abstractUriParse = AddressUriParser(null, null)
            return when (val result = abstractUriParse.parse(address)) {
                is AddressUriResult.Uri -> {
                    if (BlockchainType.supported.map { it.uriScheme }
                            .contains(result.addressUri.scheme))
                        result.addressUri
                    else
                        null
                }

                else -> null
            }
        }
        return null
    }

    private fun isZCashAddress(text: String): Boolean {
        if (!text.startsWith("t")) return false

        val address = when (val pos = text.indexOf('?')) {
            -1 -> text
            else -> text.substring(0, pos)
        }
        return ZcashAddressValidator.validate(address)
    }

    private fun handleAddressData(text: String) {
        if (text.contains("//")) {
            //handle this type of uri ton://transfer/<address>
            val toncoinAddress = ToncoinUriParser.getAddress(text) ?: return
            openSendTokenSelect = OpenSendTokenSelect(
                blockchainTypes = listOf(BlockchainType.Ton),
                tokenTypes = null,
                address = toncoinAddress,
                amount = null
            )
            emitState()
            return
        }

        val uri = uri(text)
        if (uri != null) {
            val allowedBlockchainTypes = uri.allowedBlockchainTypes
            var allowedTokenTypes: List<TokenType>? = null
            uri.value<String>(AddressUri.Field.TokenUid)?.let { uid ->
                TokenType.fromId(uid)?.let { tokenType ->
                    allowedTokenTypes = listOf(tokenType)
                }
            }

            openSendTokenSelect = OpenSendTokenSelect(
                blockchainTypes = allowedBlockchainTypes,
                tokenTypes = allowedTokenTypes,
                address = uri.address,
                amount = uri.amount
            )
            emitState()
        } else {
            val chain = addressHandlerFactory.parserChain(null)
            val types = chain.supportedAddressHandlers(text)
            if (types.isEmpty()) {
                errorMessage =
                    cash.p.terminal.strings.helpers.Translator.getString(R.string.Balance_Error_InvalidQrCode)
                emitState()
                return
            }

            openSendTokenSelect = OpenSendTokenSelect(
                blockchainTypes = types.map { it.blockchainType },
                tokenTypes = null,
                address = text,
                amount = null
            )
            emitState()
        }
    }

    private fun handleWalletConnectUri(scannedText: String) {
        WalletKit.pair(
            Pair(scannedText.trim()),
            onSuccess = {
                connectionResult = null
            },
            onError = {
                connectionResult = WalletConnectListViewModel.ConnectionResult.Error
            }
        )
    }

    fun onSendOpened() {
        openSendTokenSelect = null
        emitState()
    }

    fun errorShown() {
        errorMessage = null
        emitState()
    }

    sealed class SyncError {
        class NetworkNotAvailable : SyncError()
        class Dialog(val wallet: Wallet, val errorMessage: String?) : SyncError()
    }

    fun onResume() {
        service.resyncBalanceItems()
    }
}

sealed class ReceiveAllowedState {
    object Allowed : ReceiveAllowedState()
    data class BackupRequired(val account: Account) : ReceiveAllowedState()
}

class BackupRequiredError(val account: Account, val coinTitle: String) : Error("Backup Required")

private data class PendingSwapsSnapshot(
    val totalCount: Int,
    val singleMultiSwapId: String?,
    val singlePayCoreDate: Long?,
    val singlePayCoreRecordUid: String?,
)

data class BalanceUiState(
    val balanceViewItems: List<BalanceViewItem2>,
    val viewState: ViewState?,
    val isRefreshing: Boolean,
    val headerNote: HeaderNote,
    val errorMessage: String?,
    val openSend: OpenSendTokenSelect? = null,
    val openRestoreFromQr: OpenRestoreFromQr? = null,
    val balanceTabButtonsEnabled: Boolean,
    val showStackingForWatchAccount: Boolean,
    val sortType: BalanceSortType,
    val sortTypes: List<BalanceSortType>,
    val displayDiffOptionType: DisplayDiffOptionType,
    val displayPricePeriod: DisplayPricePeriod,
    val pendingSwapCount: Int = 0,
    val singlePendingSwapId: String? = null,
    val singlePayCoreSwapDate: Long? = null,
    val singlePayCoreSwapLoading: Boolean = false,
)

data class OpenSendTokenSelect(
    val blockchainTypes: List<BlockchainType>?,
    val tokenTypes: List<TokenType>?,
    val address: String,
    val amount: BigDecimal? = null,
)

data class OpenRestoreFromQr(
    val words: List<String>,
    val passphrase: String,
    val moneroHeight: Long?, // Non-null for 25-word Monero seeds
    val language: Language?
)

sealed class TotalUIState {
    data class Visible(
        val primaryAmountStr: String,
        val secondaryAmountStr: String,
        val dimmed: Boolean
    ) : TotalUIState()

    object Hidden : TotalUIState()

}

enum class HeaderNote {
    None,
    NonStandardAccount,
    NonRecommendedAccount
}

fun Account.headerNote(nonRecommendedDismissed: Boolean): HeaderNote = when {
    nonStandard -> HeaderNote.NonStandardAccount
    nonRecommended -> if (nonRecommendedDismissed) HeaderNote.None else HeaderNote.NonRecommendedAccount
    else -> HeaderNote.None
}

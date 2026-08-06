package cash.p.terminal.modules.managewallets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.R
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.modules.restoreaccount.restoreblockchains.CoinViewItem
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.modules.send.userMessageRes
import cash.p.terminal.tangem.domain.usecase.BuildHardwarePublicKeyUseCase
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.latestAccountOr
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.tangem.common.core.TangemSdkError.UserCancelled
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class ManageWalletsViewModel(
    private val service: ManageWalletsService,
    private val clearables: List<Clearable>
) : ViewModel(), ManageWalletsCallback {

    private val accountManager: IAccountManager by inject(IAccountManager::class.java)
    private val tangemScanUseCase: TangemScanUseCase by inject(TangemScanUseCase::class.java)
    private val hardwarePublicKeyStorage: HardwarePublicKeyStorage by inject(
        HardwarePublicKeyStorage::class.java
    )
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy by inject(
        HardwareWalletTokenPolicy::class.java
    )
    private val fetchTrezorPublicKeys: FetchTrezorPublicKeysUseCase by inject(
        FetchTrezorPublicKeysUseCase::class.java
    )
    private val addMoneroToTrezorAccount: AddMoneroToTrezorAccountUseCase by inject(
        AddMoneroToTrezorAccountUseCase::class.java
    )

    private val _groupsList = MutableStateFlow<List<CoinGroup>>(emptyList())
    override var groupsList: StateFlow<List<CoinGroup>> = _groupsList.asStateFlow()

    private val awaitingEnabledTokens = mutableSetOf<Token>()
    private val expandedGroups = mutableSetOf<String>()
    private var existingPublicKeys: List<HardwarePublicKey>? = null
    private var closeAfterSuccessfulScan = false
    private var scanToAddInProgress = false
    private var scanToAddJob: Job? = null

    override var showScanToAddButton by mutableStateOf(false)
        private set

    override var hardwareActionButtonText by mutableStateOf(
        when (accountManager.activeAccount?.type) {
            is AccountType.TrezorDevice -> App.instance.getString(R.string.add_via_trezor)
            else -> App.instance.getString(R.string.scan_card_to_add)
        }
    )
        private set

    override var errorMsg by mutableStateOf<String?>(null)
        private set

    override var closeScreen by mutableStateOf(false)
        private set

    init {
        loadData()
    }

    private fun loadData() {
        if (isHardwareWallet()) {
            viewModelScope.launch {
                accountManager.activeAccount?.let { account ->
                    existingPublicKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
                }
            }
        }
        viewModelScope.launch {
            service.itemsFlow.collect {
                sync(it)
            }
        }
        viewModelScope.launch {
            service.restoreSettingsRejectedFlow.collect(::handleRestoreSettingsRejected)
        }
    }

    fun requestScanToAddTokens(closeAfterSuccess: Boolean): Job {
        if (scanToAddInProgress) return checkNotNull(scanToAddJob)
        scanToAddInProgress = true
        return viewModelScope.launch {
            val account = accountManager.activeAccount ?: run {
                showError(App.instance.getString(R.string.error_no_active_account))
                finishScanToAdd()
                return@launch
            }
            when (account.type) {
                is AccountType.HardwareCard -> requestTangemScan(account, closeAfterSuccess)
                is AccountType.TrezorDevice -> requestTrezorKeys(account, closeAfterSuccess)
                else -> {
                    showError(App.instance.getString(R.string.error_no_active_account))
                    finishScanToAdd()
                }
            }
        }.also { scanToAddJob = it }
    }

    private suspend fun requestTangemScan(account: Account, closeAfterSuccess: Boolean) {
        val blockchainTypesToDerive = awaitingTokenQueries()
        tangemScanUseCase.scanProduct(
            blockchainsToDerive = blockchainTypesToDerive,
        ).doOnSuccess { scanResponse ->
            val publicKeys = BuildHardwarePublicKeyUseCase().invoke(
                scanResponse = scanResponse,
                accountId = account.id,
                blockchainTypeList = blockchainTypesToDerive
            )
            applyFetchedKeys(publicKeys, account, closeAfterSuccess)
        }.doOnFailure {
            finishScanToAdd()
            if (it is UserCancelled) return@doOnFailure
            showError(it.customMessage)
        }
    }

    private suspend fun requestTrezorKeys(account: Account, closeAfterSuccess: Boolean) {
        val tokenQueries = awaitingTokenQueries()
        try {
            val publicKeys = fetchTrezorPublicKeys(
                tokenQueries.filterNot { it.isMonero },
                account.id,
            )
            val refreshedAccount = accountManager.latestAccountOr(account)
            provisionMoneroIfRequested(refreshedAccount, tokenQueries)
            applyFetchedKeys(publicKeys, refreshedAccount, closeAfterSuccess)
        } catch (e: TrezorNotInitializedException) {
            finishScanToAdd()
            Timber.w(e, "Trezor is not set up")
            showError(App.instance.getString(R.string.trezor_not_initialized_description))
        } catch (_: TrezorCancelledException) {
            finishScanToAdd()
            return
        } catch (e: HardwareWalletOperationException) {
            finishScanToAdd()
            if (e.isHardwareWalletCancelled()) return
            Timber.e(e, "Trezor: failed to add Monero wallet")
            showError(App.instance.getString(e.userMessageRes()))
        } catch (e: Exception) {
            finishScanToAdd()
            Timber.e(e, "Trezor: failed to fetch public keys")
            showError(e.message)
        }
    }

    private suspend fun provisionMoneroIfRequested(
        account: Account,
        tokenQueries: List<TokenQuery>,
    ) {
        if (tokenQueries.none { it.isMonero }) return
        addMoneroToTrezorAccount.provision(account)
    }

    private fun awaitingTokenQueries(): List<TokenQuery> =
        awaitingEnabledTokens.map {
            TokenQuery(blockchainType = it.blockchainType, tokenType = it.type)
        }.distinct()

    private suspend fun applyFetchedKeys(
        publicKeys: List<HardwarePublicKey>,
        account: Account,
        closeAfterSuccess: Boolean
    ) {
        val pendingMoneroTokens = awaitingEnabledTokens.filter(Token::isMonero)
        val (addedTokens, notFoundTokens) = awaitingEnabledTokens
            .filterNot(Token::isMonero)
            .partition { token ->
                publicKeys.any {
                    it.blockchainType == token.blockchainType.uid && it.tokenType == token.type
                }
            }
        replaceAwaitingTokens(notFoundTokens + pendingMoneroTokens)

        errorMsg = if (notFoundTokens.isNotEmpty()) {
            App.instance.getString(R.string.error_hardware_wallet_some_tokens_not_found)
        } else {
            null
        }

        hardwarePublicKeyStorage.save(publicKeys)
        existingPublicKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)

        updateCloseAfterScan(closeAfterSuccess, pendingMoneroTokens)
        if (pendingMoneroTokens.isEmpty()) finishScanToAdd()
        addedTokens.forEach { service.enable(it) }
        pendingMoneroTokens.forEach(service::enable)
    }

    private fun replaceAwaitingTokens(tokens: List<Token>) {
        awaitingEnabledTokens.clear()
        awaitingEnabledTokens.addAll(tokens)
        updateNeedToShowScanToAddButton()
    }

    private fun updateCloseAfterScan(closeAfterSuccess: Boolean, pendingMoneroTokens: List<Token>) {
        closeAfterSuccessfulScan =
            closeAfterSuccess && errorMsg == null && pendingMoneroTokens.isNotEmpty()
        closeScreen =
            closeScreen ||
                (errorMsg == null && closeAfterSuccess && pendingMoneroTokens.isEmpty())
    }

    private fun showError(msg: String?) {
        errorMsg = msg
        if (msg == null) return

        viewModelScope.launch {
            delay(SnackbarDuration.LONG.value.toLong())
            errorMsg = null
        }
    }

    private fun sync(items: List<ManageWalletsService.Item>) {
        val enabledMoneroTokens = awaitingEnabledTokens.filter { token ->
            token.isMonero() && items.any { it.token == token && it.enabled }
        }
        if (enabledMoneroTokens.isNotEmpty()) {
            awaitingEnabledTokens.removeAll(enabledMoneroTokens.toSet())
            finishScanToAdd()
            updateNeedToShowScanToAddButton()
        }
        if (closeAfterSuccessfulScan && awaitingEnabledTokens.isEmpty()) {
            closeAfterSuccessfulScan = false
            closeScreen = true
        }

        val viewItems = items.map { viewItem(it) }

        val groups = viewItems.groupBy { it.item.coin.uid }
            .map { (coinUid, groupItems) ->
                val coinName = groupItems.first().item.coin.name
                CoinGroup(
                    coinName = coinName,
                    coinUid = coinUid,
                    items = groupItems,
                    isExpanded = expandedGroups.contains(coinUid)
                )
            }
        _groupsList.tryEmit(groups)
    }

    private fun handleRestoreSettingsRejected(token: Token) {
        if (!awaitingEnabledTokens.remove(token)) return
        finishScanToAdd()
        closeAfterSuccessfulScan = false
        updateNeedToShowScanToAddButton()
        sync(service.itemsFlow.value)
    }

    private fun finishScanToAdd() {
        scanToAddInProgress = false
    }

    private fun viewItem(
        item: ManageWalletsService.Item,
    ) = CoinViewItem(
        item = item.token,
        imageSource = ImageSource.Remote(
            item.token.coin.imageUrl,
            item.token.iconPlaceholder,
            item.token.coin.alternativeImageUrl
        ),
        title = item.token.coin.code,
        subtitle = item.token.coin.name,
        enabled = item.enabled || awaitingEnabledTokens.contains(item.token),
        hasInfo = item.hasInfo,
        label = item.token.badge
    )

    override fun enable(token: Token) {
        val account = accountManager.activeAccount
        if (!isHardwareWallet() || hasPublicKey(token)) {
            service.enable(token)
        } else {
            if (account != null && !hardwareWalletTokenPolicy.isSupported(account, token)) {
                showError(App.instance.getString(R.string.error_hardware_wallet_not_supported))
                return
            }
            awaitingEnabledTokens.add(token)
            updateNeedToShowScanToAddButton()
            sync(service.itemsFlow.value)
        }
    }

    override fun disable(token: Token) {
        service.disable(token)
        if (isHardwareWallet()) {
            if (awaitingEnabledTokens.remove(token)) {
                sync(service.itemsFlow.value)
            }
            updateNeedToShowScanToAddButton()
        }
    }

    override fun updateFilter(text: String) {
        service.setFilter(text)
    }

    override fun toggleGroupExpansion(coinUid: String) {
        if (expandedGroups.contains(coinUid)) {
            expandedGroups.remove(coinUid)
        } else {
            expandedGroups.add(coinUid)
        }
        sync(service.itemsFlow.value)
    }

    private fun updateNeedToShowScanToAddButton() {
        showScanToAddButton = isHardwareWallet() && awaitingEnabledTokens.isNotEmpty()
    }

    private fun hasPublicKey(token: Token): Boolean =
        existingPublicKeys?.any {
            it.blockchainType == token.blockchainType.uid && it.tokenType == token.type
        } == true

    override val addTokenEnabled: Boolean
        get() = service.accountType?.canAddTokens == true

    override fun onCleared() {
        clearables.forEach(Clearable::clear)
    }

    private fun isHardwareWallet() = accountManager.activeAccount?.isHardwareWalletAccount == true
}

data class CoinGroup(
    val coinName: String,
    val coinUid: String,
    val items: List<CoinViewItem<Token>>,
    val isExpanded: Boolean = false
) {
    val isSingleOption: Boolean
        get() = items.size == 1
}

interface ManageWalletsCallback {
    val groupsList: StateFlow<List<CoinGroup>>
    val addTokenEnabled: Boolean
    val showScanToAddButton: Boolean
    val hardwareActionButtonText: String
    val errorMsg: String?
    val closeScreen: Boolean

    fun updateFilter(text: String)
    fun enable(token: Token)
    fun disable(token: Token)
    fun toggleGroupExpansion(coinUid: String)
}

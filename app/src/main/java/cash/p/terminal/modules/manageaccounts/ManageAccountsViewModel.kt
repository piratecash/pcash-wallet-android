package cash.p.terminal.modules.manageaccounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.AccountViewItem
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

class ManageAccountsViewModel(
    private val accountManager: IAccountManager,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val mode: ManageAccountsModule.Mode
) : ViewModel() {

    var premiumAccountsState by mutableStateOf<List<AccountViewItem>?>(null)
    var regularAccountsState by mutableStateOf<List<AccountViewItem>?>(null)
    var watchAccountsState by mutableStateOf<List<AccountViewItem>?>(null)
    var hardwareAccountsState by mutableStateOf<List<AccountViewItem>?>(null)

    var finish by mutableStateOf(false)

    private var accountPremiumTypes: Map<String, PremiumType> = emptyMap()

    init {
        viewModelScope.launch {
            // Take the initial snapshot only AFTER the subscription is registered (via onSubscription)
            // so a concurrent account import — accountsFlow has replay = 0 — cannot be dropped in the
            // gap between snapshot and subscription. Fall back to onStart if the flow is not a SharedFlow.
            val accountsFlow = accountManager.accountsFlow
            val withInitialSnapshot: Flow<List<Account>> = if (accountsFlow is SharedFlow) {
                accountsFlow.onSubscription { emit(accountManager.accounts) }
            } else {
                accountsFlow.onStart { emit(accountManager.accounts) }
            }
            withInitialSnapshot.collectLatest {
                // Flow emissions are invalidation signals; always render the full account cache
                // (import() can emit only the newly added account).
                updateViewItems(accountManager.activeAccount, accountManager.accounts)
            }
        }

        viewModelScope.launch {
            // Observe the persisted+background-refreshed premium cache: shows the hydrated type immediately
            // on a cold start and re-renders badges as the background re-scan completes.
            checkPremiumUseCase.premiumTypesFlow.collect { premiumTypes ->
                accountPremiumTypes = premiumTypes
                updateViewItems(accountManager.activeAccount, accountManager.accounts)
            }
        }

        viewModelScope.launch {
            accountManager.activeAccountStateFlow
                .collect { activeAccountState ->
                    if (activeAccountState is ActiveAccountState.ActiveAccount) {
                        updateViewItems(activeAccountState.account, accountManager.accounts)
                    }
                }
        }
    }

    private fun updateViewItems(activeAccount: Account?, accounts: List<Account>) {
        val groups = accounts.groupByPremium(accountPremiumTypes)
        premiumAccountsState = groups.premium.map { getViewItem(it, activeAccount) }
        regularAccountsState = groups.other.map { getViewItem(it, activeAccount) }
        watchAccountsState = groups.watch.map { getViewItem(it, activeAccount) }
        hardwareAccountsState = groups.hardware.map { getViewItem(it, activeAccount) }
    }

    private fun getViewItem(account: Account, activeAccount: Account?) =
        AccountViewItem(
            accountId = account.id,
            title = account.name,
            subtitle = account.type.detailedDescription,
            selected = account == activeAccount,
            backupRequired = account.supportsBackup && !account.isBackedUp && !account.isFileBackedUp,
            showAlertIcon = account.supportsBackup && (!account.isBackedUp || account.nonStandard || account.nonRecommended),
            isHardwareWallet = account.isHardwareWalletAccount,
            showNfcIcon = account.type is AccountType.HardwareCard,
            migrationRequired = account.nonStandard,
            premiumType = account.resolvedPremiumType(accountPremiumTypes),
        )

    fun onSelect(accountViewItem: AccountViewItem) {
        accountManager.setActiveAccountId(accountViewItem.accountId)

        if (mode == ManageAccountsModule.Mode.Switcher) {
            finish = true
        }
    }
}

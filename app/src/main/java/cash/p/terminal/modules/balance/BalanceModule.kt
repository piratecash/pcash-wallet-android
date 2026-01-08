package cash.p.terminal.modules.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.balance.BalanceWarning
import cash.p.terminal.wallet.managers.IBalanceHiddenManager

object BalanceModule {
    class AccountsFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BalanceAccountsViewModel(getKoinInstance()) as T
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val totalService = TotalService(
                currencyManager = getKoinInstance(),
                marketKit = getKoinInstance(),
                baseTokenManager = App.baseTokenManager,
                balanceHiddenManager = getKoinInstance()
            )
            return BalanceViewModel(
                service = DefaultBalanceService.getInstance("wallet"),
                balanceViewItemFactory = BalanceViewItemFactory(),
                balanceViewTypeManager = App.balanceViewTypeManager,
                totalBalance = TotalBalance(totalService, getKoinInstance()),
                localStorage = getKoinInstance(),
                wCManager = App.wcManager,
                addressHandlerFactory = AddressHandlerFactory(AppConfigProvider.udnApiKey),
                priceManager = App.priceManager,
                balanceHiddenManager = getKoinInstance<IBalanceHiddenManager>(),
            ) as T
        }
    }

    val BalanceWarning.warningText: WarningText
        get() = when (this) {
            BalanceWarning.TronInactiveAccountWarning -> WarningText(
                title = TranslatableString.ResString(R.string.Tron_TokenPage_AddressNotActive_Title),
                text = TranslatableString.ResString(R.string.Tron_TokenPage_AddressNotActive_Info),
            )
        }
}
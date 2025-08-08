package cash.p.terminal.modules.createaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.wallet.PassphraseValidator

object CreateAccountModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateAdvancedAccountViewModel(
                accountFactory = App.accountFactory,
                wordsManager = App.wordsManager,
                accountManager = App.accountManager,
                walletActivator = App.walletActivator,
                passphraseValidator = PassphraseValidator(),
                predefinedBlockchainSettingsProvider = PredefinedBlockchainSettingsProvider(
                    App.restoreSettingsManager,
                    App.zcashBirthdayProvider
                )
            ) as T
        }
    }
}

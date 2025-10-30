package cash.p.terminal.modules.createaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.wallet.PassphraseValidator
import org.koin.java.KoinJavaComponent.inject

object CreateAccountModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val predefinedBlockchainSettingsProvider: PredefinedBlockchainSettingsProvider by inject(PredefinedBlockchainSettingsProvider::class.java)
            val restoreSettingsManager: RestoreSettingsManager by inject(RestoreSettingsManager::class.java)
            return CreateAdvancedAccountViewModel(
                accountFactory = App.accountFactory,
                wordsManager = App.wordsManager,
                accountManager = App.accountManager,
                walletActivator = App.walletActivator,
                passphraseValidator = PassphraseValidator(),
                predefinedBlockchainSettingsProvider = predefinedBlockchainSettingsProvider,
                restoreSettingsManager = restoreSettingsManager
            ) as T
        }
    }
}

package cash.p.terminal.modules.settings.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.IBackupManager
import org.koin.java.KoinJavaComponent.get

object MainSettingsModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val viewModel = MainSettingsViewModel(
                get(IBackupManager::class.java),
                App.systemInfoManager,
                App.termsManager,
                App.pinComponent,
                App.wcSessionManager,
                App.wcManager,
                App.accountManager,
                App.languageManager,
                App.currencyManager,
            )

            return viewModel as T
        }
    }

    sealed class CounterType {
        class SessionCounter(val number: Int) : CounterType()
        class PendingRequestCounter(val number: Int) : CounterType()
    }

}

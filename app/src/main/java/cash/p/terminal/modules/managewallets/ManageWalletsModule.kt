package cash.p.terminal.modules.managewallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.modules.receive.FullCoinsProvider
import org.koin.java.KoinJavaComponent.inject

object ManageWalletsModule {

    class Factory : ViewModelProvider.Factory {

        private val restoreSettingsManager: RestoreSettingsManager by inject(RestoreSettingsManager::class.java)
        private val userDeletedWalletManager: UserDeletedWalletManager by inject(UserDeletedWalletManager::class.java)
        private val restoreSettingsService by lazy {
            RestoreSettingsService(restoreSettingsManager, App.zcashBirthdayProvider)
        }

        private val manageWalletsService by lazy {
            val activeAccount = App.accountManager.activeAccount
            ManageWalletsService(
                walletManager = App.walletManager,
                restoreSettingsService = restoreSettingsService,
                fullCoinsProvider = App.accountManager.activeAccount?.let { account ->
                    FullCoinsProvider(App.marketKit, account)
                },
                account = activeAccount,
                userDeletedWalletManager = userDeletedWalletManager
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                RestoreSettingsViewModel::class.java -> {
                    RestoreSettingsViewModel(restoreSettingsService, listOf(restoreSettingsService)) as T
                }
                ManageWalletsViewModel::class.java -> {
                    ManageWalletsViewModel(manageWalletsService, listOf(manageWalletsService)) as T
                }
                else -> throw IllegalArgumentException()
            }
        }
    }
}

package cash.p.terminal.modules.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.wallet.Account
import cash.p.terminal.modules.walletconnect.WCManager
import kotlinx.parcelize.Parcelize

object MainModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                App.pinComponent,
                App.rateAppManager,
                App.backupManager,
                App.termsManager,
                App.accountManager,
                App.releaseNotesManager,
                App.localStorage,
                App.wcSessionManager,
                App.wcManager,
            ) as T
        }
    }

    fun start(context: Context, data: Uri? = null) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.data = data
        context.startActivity(intent)
    }

    fun startAsNewTask(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun startAsNewTask(context: Activity) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        context.overridePendingTransition(0, 0)
    }

    sealed class BadgeType {
        object BadgeDot : BadgeType()
        class BadgeNumber(val number: Int) : BadgeType()
    }

    data class NavigationViewItem(
        val mainNavItem: MainNavigation,
        val selected: Boolean,
        val enabled: Boolean,
        val badge: BadgeType? = null
    )

    @Parcelize
    enum class MainNavigation(val iconRes: Int, val titleRes: Int) : Parcelable {
        Market(R.drawable.ic_market_24, R.string.Market_Title),
        Balance(R.drawable.ic_wallet_24, R.string.Balance_Title),
        Transactions(R.drawable.ic_transactions, R.string.Transactions_Title),
        Settings(R.drawable.ic_settings, R.string.Settings_Title);

        companion object {
            private val map = values().associateBy(MainNavigation::name)

            fun fromString(type: String?): MainNavigation? = map[type]
        }
    }

    data class UiState(
        val selectedTabIndex: Int,
        val deeplinkPage: DeeplinkPage?,
        val mainNavItems: List<NavigationViewItem>,
        val showRateAppDialog: Boolean,
        val contentHidden: Boolean,
        val showWhatsNew: Boolean,
        val activeWallet: Account?,
        val torEnabled: Boolean,
        val wcSupportState: WCManager.SupportState?
    )
}

data class DeeplinkPage(
    val navigationId: Int,
    val input: Parcelable
)

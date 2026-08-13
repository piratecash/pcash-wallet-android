package cash.p.terminal.modules.main

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import cash.p.terminal.core.App
import cash.p.terminal.modules.balance.OpenSendTokenSelect
import cash.p.terminal.modules.settings.appearance.AppIcon
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.wallet.Account

object MainModule {
    fun start(context: Context, data: Uri? = null) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.data = data
        context.startActivity(intent)
    }

    fun startAsNewTask(context: Context) {
        val component = launcherComponent(context)
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0) // To recreate the process to fix keystore issues
    }

    fun startAsNewTask(context: Activity) {
        val intent = Intent.makeRestartActivityTask(launcherComponent(context))
        context.finishAndRemoveTask()
        context.startActivity(intent)
        context.overridePendingTransition(0, 0)
    }

    private fun launcherComponent(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            (App.localStorage.appIcon ?: AppIcon.Main).launcherName
        )
    }

    sealed class BadgeType {
        object BadgeDot : BadgeType()
        class BadgeNumber(val number: Int) : BadgeType()
    }

    data class NavigationViewItem(
        val mainNavItem: MainDestination,
        val selected: Boolean,
        val enabled: Boolean,
        val badge: BadgeType? = null
    )

    data class UiState(
        val selectedTabIndex: Int,
        val deeplinkPage: DeeplinkPage?,
        val mainNavItems: List<NavigationViewItem>,
        val contentHidden: Boolean,
        val showWhatsNew: Boolean,
        val activeWallet: Account?,
        val torEnabled: Boolean,
        val wcSupportState: WCManager.SupportState?,
        val openSend: OpenSendTokenSelect?,
        val walletSwitchPremiumTypes: Map<String, PremiumType> = emptyMap(),
    )
}

data class DeeplinkPage(
    val navigationId: Int,
    val input: Parcelable?
)

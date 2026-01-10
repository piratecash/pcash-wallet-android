package cash.p.terminal.modules.settings.appearance

import android.content.ComponentName
import android.content.pm.PackageManager
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.Select
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppIconService(private val localStorage: ILocalStorage) {
    private val appIcons by lazy { AppIcon.entries }

    private val _optionsFlow = MutableStateFlow(
        Select(localStorage.appIcon ?: AppIcon.Main, appIcons)
    )
    val optionsFlow = _optionsFlow.asStateFlow()

    init {
        migrateFromLegacyIconIfNeeded()
    }

    /**
     * Migrates users from legacy app icons (Dark, Duck, IVFun, Mono, Yellow) to Main.
     * These icons were removed but users may still have them stored in preferences.
     * When a stored icon name doesn't match any current AppIcon, we migrate to Main.
     */
    private fun migrateFromLegacyIconIfNeeded() {
        val rawIconName = localStorage.appIconRaw
        // If there's a stored value but it doesn't map to a valid AppIcon, it's a legacy icon
        if (rawIconName != null && localStorage.appIcon == null) {
            setAppIcon(AppIcon.Main)
        }
    }

    fun setAppIcon(appIcon: AppIcon) {
        localStorage.appIcon = appIcon

        _optionsFlow.update {
            Select(appIcon, appIcons)
        }

        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        AppIcon.entries.forEach { item ->
            App.instance.packageManager.setComponentEnabledSetting(
                ComponentName(App.instance, item.launcherName),
                if (appIcon == item) enabled else disabled,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

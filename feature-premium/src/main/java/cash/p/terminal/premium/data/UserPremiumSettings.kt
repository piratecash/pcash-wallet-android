package cash.p.terminal.premium.data

import android.content.SharedPreferences
import androidx.core.content.edit
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.managers.UserManager

class UserPremiumSettings(
    private val preferences: SharedPreferences,
    private val userManager: UserManager
) : PremiumSettings {

    private fun keyForLevel(key: String, level: Int) = "${key}_$level"

    override fun getAmlCheckReceivedEnabled(): Boolean =
        preferences.getBoolean(keyForLevel("aml_check_received_enabled", userManager.getUserLevel()), false)

    override fun setAmlCheckReceivedEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(keyForLevel("aml_check_received_enabled", userManager.getUserLevel()), enabled) }
    }

    override fun getAmlCheckShowAlert(): Boolean =
        preferences.getBoolean(keyForLevel("aml_check_received_show_alert", userManager.getUserLevel()), true)

    override fun setAmlCheckShowAlert(show: Boolean) {
        preferences.edit { putBoolean(keyForLevel("aml_check_received_show_alert", userManager.getUserLevel()), show) }
    }
}

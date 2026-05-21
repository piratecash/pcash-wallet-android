package cash.p.terminal.modules.paycore

import cash.p.terminal.BuildConfig
import io.horizontalsystems.core.ILanguageManager
import java.util.Locale

class PayCoreFeatureToggle(
    private val languageManager: ILanguageManager
) {
    fun isEnabled(): Boolean {
        if (!BuildConfig.PAYCORE_ENABLED) return false
        val appLanguage = languageManager.currentLanguage
        val systemLanguage = Locale.getDefault().language
        return appLanguage != "uk" && systemLanguage != "uk"
    }
}

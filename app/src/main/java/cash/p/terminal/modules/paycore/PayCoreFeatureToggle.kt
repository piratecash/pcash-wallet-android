package cash.p.terminal.modules.paycore

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.SystemLanguageProvider
import io.horizontalsystems.core.ILanguageManager

class PayCoreFeatureToggle(
    private val languageManager: ILanguageManager,
    private val systemLanguageProvider: SystemLanguageProvider,
) {
    fun isEnabled(): Boolean {
        if (!BuildConfig.PAYCORE_ENABLED) return false
        val appLanguage = languageManager.currentLanguage
        val systemLanguage = systemLanguageProvider.language
        return appLanguage != UKRAINIAN && systemLanguage != UKRAINIAN
    }

    private companion object {
        const val UKRAINIAN = "uk"
    }
}

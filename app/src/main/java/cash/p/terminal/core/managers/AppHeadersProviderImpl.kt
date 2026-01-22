package cash.p.terminal.core.managers

import cash.p.terminal.network.data.AppHeadersProvider
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.ISystemInfoManager

class AppHeadersProviderImpl(
    systemInfoManager: ISystemInfoManager,
    private val languageManager: ILanguageManager
) : AppHeadersProvider {
    override val appVersion: String = systemInfoManager.appVersionFull
    override val currentLanguage: String get() = languageManager.currentLanguage
    override val appSignature: String? = systemInfoManager.getSigningCertFingerprint()
}

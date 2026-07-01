package cash.p.terminal.modules.paycore

import cash.p.terminal.core.managers.SystemLanguageProvider
import io.horizontalsystems.core.ILanguageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCoreFeatureToggleTest {

    private fun toggle(appLanguage: String, systemLanguage: String): PayCoreFeatureToggle {
        val languageManager = mockk<ILanguageManager> {
            every { currentLanguage } returns appLanguage
        }
        val systemLanguageProvider = mockk<SystemLanguageProvider> {
            every { language } returns systemLanguage
        }
        return PayCoreFeatureToggle(languageManager, systemLanguageProvider)
    }

    @Test
    fun isEnabled_appEnglishSystemEnglish_returnsTrue() {
        assertTrue(toggle(appLanguage = "en", systemLanguage = "en").isEnabled())
    }

    @Test
    fun isEnabled_appUkrainianSystemEnglish_returnsFalse() {
        assertFalse(toggle(appLanguage = "uk", systemLanguage = "en").isEnabled())
    }

    @Test
    fun isEnabled_appEnglishSystemUkrainian_returnsFalse() {
        assertFalse(toggle(appLanguage = "en", systemLanguage = "uk").isEnabled())
    }

    @Test
    fun isEnabled_appUkrainianSystemUkrainian_returnsFalse() {
        assertFalse(toggle(appLanguage = "uk", systemLanguage = "uk").isEnabled())
    }
}

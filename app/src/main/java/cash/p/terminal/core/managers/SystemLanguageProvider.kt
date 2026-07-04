package cash.p.terminal.core.managers

import android.content.res.Resources

/**
 * Language of the device itself, unaffected by the in-app language override.
 *
 * The app rewrites the JVM default locale via [java.util.Locale.setDefault] when the user
 * picks an interface language, so [java.util.Locale.getDefault] no longer reflects the device
 * language. System resources are read from the untouched global configuration instead.
 */
interface SystemLanguageProvider {
    val language: String
}

class SystemLanguageProviderImpl : SystemLanguageProvider {
    override val language: String
        get() = Resources.getSystem().configuration.locales.get(0).language
}

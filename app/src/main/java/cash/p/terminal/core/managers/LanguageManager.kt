package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.strings.helpers.LocaleHelper
import java.util.*

class LanguageManager {

    val fallbackLocale by LocaleHelper::fallbackLocale

    var currentLocale: Locale = App.instance.getLocale()
        set(value) {
            field = value

            App.instance.setLocale(currentLocale)
        }

    var currentLocaleTag: String
        get() = currentLocale.toLanguageTag()
        set(value) {
            currentLocale = Locale.forLanguageTag(value)
        }

    val currentLanguageName: String
        get() = getName(currentLocaleTag)

    val currentLanguage: String
        get() = currentLocale.language

    fun getName(tag: String): String {
        return Locale.forLanguageTag(tag)
            .getDisplayName(currentLocale)
            .replaceFirstChar(Char::uppercase)
    }

    fun getNativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar(Char::uppercase)
    }

}

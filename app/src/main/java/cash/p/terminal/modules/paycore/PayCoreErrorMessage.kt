package cash.p.terminal.modules.paycore

import cash.p.terminal.R
import cash.p.terminal.core.isNoInternetException
import cash.p.terminal.strings.helpers.Translator

internal fun Throwable.payCoreUserMessage(defaultMessage: String? = null): String {
    return when {
        isNoInternetException() -> Translator.getString(R.string.Hud_Text_NoInternet)
        !message.isNullOrBlank() -> requireNotNull(message)
        !defaultMessage.isNullOrBlank() -> defaultMessage
        else -> Translator.getString(R.string.Error)
    }
}

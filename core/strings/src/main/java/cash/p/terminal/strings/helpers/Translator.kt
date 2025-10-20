package cash.p.terminal.strings.helpers

import androidx.annotation.StringRes

object Translator {

    fun getString(@StringRes id: Int): String {
        return runCatching {
            getLocalAwareContext().getString(id)
        }.getOrElse { "Preview mode" }
    }

    fun getString(@StringRes id: Int, vararg params: Any): String {
        return runCatching {
            getLocalAwareContext().getString(id, *params)
        }.getOrElse { "Preview mode" }
    }

    private fun getLocalAwareContext() =
        LocaleHelper.onAttach(LibraryInitializer.getApplicationContext())
}

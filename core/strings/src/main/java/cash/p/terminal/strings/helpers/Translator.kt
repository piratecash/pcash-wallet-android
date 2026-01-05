package cash.p.terminal.strings.helpers

import androidx.annotation.ArrayRes
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

    fun getStringArrayItem(@ArrayRes arrayId: Int, index: Int): String {
        return runCatching {
            getLocalAwareContext().resources.getStringArray(arrayId)[index]
        }.getOrElse { "Preview mode" }
    }

    private fun getLocalAwareContext() =
        LocaleHelper.onAttach(LibraryInitializer.getApplicationContext())
}

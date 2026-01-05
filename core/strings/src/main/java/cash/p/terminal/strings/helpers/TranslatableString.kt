package cash.p.terminal.strings.helpers

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

sealed class TranslatableString {
    class PlainString(val text: String) : TranslatableString()
    class ResString(@StringRes val id: Int, vararg val formatArgs: Any) : TranslatableString()
    class ResArrayString(@ArrayRes val arrayId: Int, val index: Int) : TranslatableString()

    @Composable
    fun getString(): String {
        return when (val s = this) {
            is PlainString -> s.text
            is ResString -> stringResource(s.id, *s.formatArgs)
            is ResArrayString -> LocalContext.current.resources.getStringArray(s.arrayId)[s.index]
        }
    }

    override fun toString(): String {
        return when (val s = this) {
            is PlainString -> s.text
            is ResString -> Translator.getString(s.id, *s.formatArgs)
            is ResArrayString -> Translator.getStringArrayItem(s.arrayId, s.index)
        }
    }
}

interface WithTranslatableTitle {
    val title: TranslatableString
}

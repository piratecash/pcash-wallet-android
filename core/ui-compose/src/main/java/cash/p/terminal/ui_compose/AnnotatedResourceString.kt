package cash.p.terminal.ui_compose

import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnnotatedResourceString {
    fun htmlToAnnotatedString(htmlString: String, preserveNewlines: Boolean = false): AnnotatedString {
        val processedHtml = if (preserveNewlines) htmlString.replace("\n", "<br>") else htmlString
        val spanned = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_LEGACY)
        return spannedToAnnotatedString(spanned)
    }

    fun spannedToAnnotatedString(spanned: CharSequence): AnnotatedString {
        val text = spanned.toString().trimEnd()
        return buildAnnotatedString {
            append(text)

            if (spanned is Spanned) {
                spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
                    val start = spanned.getSpanStart(span).coerceAtMost(text.length)
                    val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
                    if (start < end) {
                        when (span) {
                            is StyleSpan -> {
                                if (span.style == android.graphics.Typeface.BOLD) {
                                    addStyle(
                                        style = SpanStyle(fontWeight = FontWeight.Bold),
                                        start = start,
                                        end = end
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun annotatedStringResource(@StringRes id: Int): AnnotatedString {
    val context = LocalContext.current
    return remember(id) {
        val text = context.resources.getText(id)
        AnnotatedResourceString.spannedToAnnotatedString(text)
    }
}

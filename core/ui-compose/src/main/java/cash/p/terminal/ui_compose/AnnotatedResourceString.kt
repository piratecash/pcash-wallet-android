package cash.p.terminal.ui_compose

import android.text.Html
import android.text.style.StyleSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnnotatedResourceString {
    fun htmlToAnnotatedString(htmlString: String): AnnotatedString {
        val spanned = Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY)
        return buildAnnotatedString {
            append(spanned.toString())

            spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
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
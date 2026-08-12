package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.caption_lucian

/**
 * A [FormsInput] for entering a restore height, with a trailing calendar affordance (inside the
 * field) that opens a date picker. The delete button is suppressed to match the design, which
 * shows only the calendar (and, when [pasteEnabled], a Paste button).
 */
@Composable
fun RestoreHeightInput(
    initial: String?,
    hint: String,
    error: String?,
    pasteEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier,
    numericOnly: Boolean = false,
) {
    Column(modifier = modifier) {
        FormsInput(
            initial = initial,
            pasteEnabled = pasteEnabled,
            singleLine = true,
            hint = hint,
            trailingIcon = R.drawable.ic_calendar_20,
            onTrailingIconClick = onCalendarClick,
            showDeleteButton = false,
            keyboardOptions = KeyboardOptions(
                // Number keyboard for a pure block-height field; Ascii where a date (with dashes)
                // may also be typed.
                keyboardType = if (numericOnly) KeyboardType.Number else KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            textPreprocessor = if (numericOnly) DigitsOnlyTextPreprocessor else TextPreprocessorImpl,
            onValueChange = onValueChange,
        )
        error?.let { errorText ->
            Spacer(Modifier.height(8.dp))
            caption_lucian(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = errorText
            )
        }
    }
}

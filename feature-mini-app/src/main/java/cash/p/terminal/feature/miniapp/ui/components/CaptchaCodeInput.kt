package cash.p.terminal.feature.miniapp.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun CaptchaCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    codeLength: Int = 5
) {
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        BasicTextField(
            value = code,
            onValueChange = { newValue ->
                // Filter to alphanumeric, uppercase, and limit to codeLength
                val filtered = newValue
                    .uppercase()
                    .filter { it.isLetterOrDigit() }
                    .take(codeLength)
                onCodeChange(filtered)
            },
            enabled = enabled,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { hasFocus = it.isFocused }
                .fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            // Hide the default cursor since we draw our own in the cells
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = {
                // Visual character cells
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(codeLength) { index ->
                        CharacterCell(
                            char = code.getOrNull(index),
                            isFocused = enabled && hasFocus && code.length == index,
                            isError = isError,
                            enabled = enabled
                        )
                    }
                }
            }
        )

        // Error message
        if (errorMessage != null) {
            VSpacer(8.dp)
            Text(
                text = errorMessage,
                style = ComposeAppTheme.typography.caption,
                color = ComposeAppTheme.colors.lucian,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CharacterCell(
    char: Char?,
    isFocused: Boolean,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isError -> ComposeAppTheme.colors.lucian
        !enabled -> ComposeAppTheme.colors.steel20.copy(alpha = 0.5f)
        else -> ComposeAppTheme.colors.steel20
    }

    val backgroundColor = if (enabled) {
        ComposeAppTheme.colors.lawrence
    } else {
        ComposeAppTheme.colors.lawrence.copy(alpha = 0.5f)
    }

    val textColor = if (enabled) {
        ComposeAppTheme.colors.leah
    } else {
        ComposeAppTheme.colors.grey50
    }

    val cursorColor = ComposeAppTheme.colors.jacob

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .drawWithContent {
                drawContent()
                // Draw cursor when focused and empty
                if (isFocused) {
                    val cursorHeight = size.height * 0.5f
                    val cursorX = size.width / 2
                    val cursorTopY = (size.height - cursorHeight) / 2
                    drawLine(
                        color = cursorColor,
                        start = Offset(cursorX, cursorTopY),
                        end = Offset(cursorX, cursorTopY + cursorHeight),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = ComposeAppTheme.typography.title3,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaCodeInputEmptyPreview() {
    ComposeAppTheme {
        CaptchaCodeInput(
            code = "",
            onCodeChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaCodeInputPartialPreview() {
    ComposeAppTheme {
        CaptchaCodeInput(
            code = "A7K",
            onCodeChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaCodeInputFullPreview() {
    ComposeAppTheme {
        CaptchaCodeInput(
            code = "A7K9Q",
            onCodeChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaCodeInputErrorPreview() {
    ComposeAppTheme {
        CaptchaCodeInput(
            code = "A7K9Q",
            onCodeChange = {},
            isError = true,
            errorMessage = "Wrong code, please try again",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaCodeInputDisabledPreview() {
    ComposeAppTheme {
        CaptchaCodeInput(
            code = "A7K",
            onCodeChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

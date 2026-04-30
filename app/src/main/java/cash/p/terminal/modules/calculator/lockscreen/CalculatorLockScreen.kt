package cash.p.terminal.modules.calculator.lockscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class CalculatorLockScreenActions(
    val onDigit: (Char) -> Unit,
    val onOperator: (Char) -> Unit,
    val onDecimal: () -> Unit,
    val onParen: () -> Unit,
    val onToggleSign: () -> Unit,
    val onDelete: () -> Unit,
    val onClear: () -> Unit,
    val onEquals: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorLockScreen(
    uiState: CalculatorLockScreenUiState,
    actions: CalculatorLockScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.tyler
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.weight(1f))
            DisplayBlock(
                expression = uiState.expression.ifEmpty { "0" },
                result = uiState.displayedResult,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) { Spacer(Modifier.weight(1f)) }
                CalculatorCell(
                    CalculatorButton.IconLabel(
                        R.drawable.ic_backspace,
                        ButtonStyle.Transparent,
                        actions.onDelete,
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                thickness = 0.5.dp,
                color = ComposeAppTheme.colors.steel20,
            )
            Keypad(
                actions = actions,
                decimalSeparator = uiState.decimalSeparator,
            )
        }
    }
}

@Composable
private fun DisplayBlock(
    expression: String,
    result: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        AutoSizingDisplayLine(
            text = expression,
            color = ComposeAppTheme.colors.grey,
            maxFontSize = 32.sp,
        )
        AutoSizingDisplayLine(
            text = result,
            color = ComposeAppTheme.colors.leah,
            maxFontSize = 90.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun AutoSizingDisplayLine(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text = text,
        modifier = modifier
            .fillMaxWidth(),
        style = TextStyle(
            color = color,
            fontWeight = fontWeight,
            textAlign = TextAlign.End,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = AUTO_SIZE_MIN,
            maxFontSize = maxFontSize,
            stepSize = 1.sp,
        ),
    )
}

@Composable
private fun Keypad(
    actions: CalculatorLockScreenActions,
    decimalSeparator: Char,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeypadRow(
            CalculatorButton.TextLabel("C", ButtonStyle.Digit, actions.onClear),
            CalculatorButton.TextLabel("( )", ButtonStyle.Digit, actions.onParen),
            CalculatorButton.TextLabel("%", ButtonStyle.Digit) { actions.onOperator('%') },
            CalculatorButton.TextLabel("÷", ButtonStyle.Operator) { actions.onOperator('÷') },
        )
        KeypadRow(
            digitButton('1', actions),
            digitButton('2', actions),
            digitButton('3', actions),
            CalculatorButton.TextLabel("×", ButtonStyle.Operator) { actions.onOperator('×') },
        )
        KeypadRow(
            digitButton('4', actions),
            digitButton('5', actions),
            digitButton('6', actions),
            CalculatorButton.TextLabel("−", ButtonStyle.Operator) { actions.onOperator('-') },
        )
        KeypadRow(
            digitButton('7', actions),
            digitButton('8', actions),
            digitButton('9', actions),
            CalculatorButton.TextLabel("+", ButtonStyle.Operator) { actions.onOperator('+') },
        )
        KeypadRow(
            CalculatorButton.IconLabel(
                R.drawable.ic_plus_minus_24,
                ButtonStyle.Digit,
                actions.onToggleSign
            ),
            digitButton('0', actions),
            CalculatorButton.TextLabel(
                decimalSeparator.toString(),
                ButtonStyle.Digit,
                actions.onDecimal
            ),
            CalculatorButton.TextLabel("=", ButtonStyle.Equals, actions.onEquals),
        )
    }
}

private fun digitButton(digit: Char, actions: CalculatorLockScreenActions): CalculatorButton =
    CalculatorButton.TextLabel(digit.toString(), ButtonStyle.Digit) { actions.onDigit(digit) }

@Composable
private fun KeypadRow(vararg buttons: CalculatorButton) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (button in buttons) {
            CalculatorCell(button)
        }
    }
}

@Composable
private fun RowScope.CalculatorCell(button: CalculatorButton) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        CircleButton(
            style = button.style,
            onClick = button.onClick,
        ) { fontSize ->
            when (button) {
                is CalculatorButton.TextLabel -> Text(
                    text = button.text,
                    color = button.style.contentColor(),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                )

                is CalculatorButton.IconLabel -> Icon(
                    painter = painterResource(id = button.iconRes),
                    contentDescription = null,
                    tint = button.style.contentColor(),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleButton(
    style: ButtonStyle,
    onClick: () -> Unit,
    content: @Composable (TextUnit) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val onKeyPress = rememberHapticKeyPress(onClick)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(style.backgroundColor())
            .then(
                if (style.hasBorder) {
                    Modifier.border(1.dp, ComposeAppTheme.colors.steel20, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                onClick = onKeyPress,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = style.contentColor()),
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(style.textSize)
    }
}

@Composable
private fun rememberHapticKeyPress(onClick: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember(context, onClick) {
        {
            HudHelper.vibrate(context, durationMs = KEY_TAP_VIBRATION_MS)
            onClick()
        }
    }
}

private const val KEY_TAP_VIBRATION_MS = 1L

private val AUTO_SIZE_MIN = 4.sp
private val KEY_TEXT_SIZE = 28.sp
private val OPERATOR_KEY_TEXT_SIZE = 48.sp

private sealed interface CalculatorButton {
    val style: ButtonStyle
    val onClick: () -> Unit

    data class TextLabel(
        val text: String,
        override val style: ButtonStyle,
        override val onClick: () -> Unit,
    ) : CalculatorButton

    data class IconLabel(
        val iconRes: Int,
        override val style: ButtonStyle,
        override val onClick: () -> Unit,
    ) : CalculatorButton
}

private enum class ButtonStyle {
    Digit, Operator, Equals, Transparent;

    val hasBorder: Boolean get() = this != Transparent

    val textSize: TextUnit
        get() = when (this) {
            Operator, Equals -> OPERATOR_KEY_TEXT_SIZE
            Digit, Transparent -> KEY_TEXT_SIZE
        }

    @Composable
    fun backgroundColor(): Color = when (this) {
        Digit -> ComposeAppTheme.colors.lawrence
        Operator -> ComposeAppTheme.colors.grey
        Equals -> ComposeAppTheme.colors.jacob
        Transparent -> Color.Transparent
    }

    @Composable
    fun contentColor(): Color = when (this) {
        Digit -> ComposeAppTheme.colors.leah
        Operator -> ComposeAppTheme.colors.leah
        Equals -> ComposeAppTheme.colors.leah
        Transparent -> ComposeAppTheme.colors.jacob
    }
}

private val PreviewActions = CalculatorLockScreenActions(
    onDigit = {},
    onOperator = {},
    onDecimal = {},
    onParen = {},
    onToggleSign = {},
    onDelete = {},
    onClear = {},
    onEquals = {},
)

private val PreviewState = CalculatorLockScreenUiState(
    expression = "6,291÷5",
    displayedResult = "1,258.2",
    decimalSeparator = ',',
    unlocked = false,
)

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, heightDp = 740)
@Composable
private fun CalculatorLockScreenLightPreview() {
    ComposeAppTheme(darkTheme = false) {
        CalculatorLockScreen(
            uiState = PreviewState,
            actions = PreviewActions,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, heightDp = 740)
@Composable
private fun CalculatorLockScreenDarkPreview() {
    ComposeAppTheme(darkTheme = true) {
        CalculatorLockScreen(
            uiState = PreviewState,
            actions = PreviewActions,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, heightDp = 740)
@Composable
private fun CalculatorLockScreenEmptyDarkPreview() {
    ComposeAppTheme(darkTheme = true) {
        CalculatorLockScreen(
            uiState = CalculatorLockScreenUiState(
                expression = "",
                displayedResult = "0",
                decimalSeparator = '.',
                unlocked = false,
            ),
            actions = PreviewActions,
        )
    }
}

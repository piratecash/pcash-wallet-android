package cash.p.terminal.modules.memo

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.address.MemoPrefill
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun HSMemoInput(
    maxLength: Int,
    memoPrefill: MemoPrefill,
    onValueChange: (String) -> Unit,
    initial: String = "",
    visible: Boolean = true,
) {
    var memo by rememberSaveable { mutableStateOf(initial) }
    var isInitialized by rememberSaveable { mutableStateOf(initial.isNotEmpty()) }
    var handledEventInComposition by remember { mutableStateOf(false) }
    val currentMemoPrefill by rememberUpdatedState(memoPrefill)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(memoPrefill.event?.id, visible) {
        val event = currentMemoPrefill.event
        if (event != null) {
            if (!event.isNavigationPrefill || !isInitialized) {
                memo = event.memo.takeIf { it.length <= maxLength }.orEmpty()
                isInitialized = true
            }
            if (visible) currentOnValueChange(memo)
            handledEventInComposition = true
            currentMemoPrefill.handled(event.id)
        } else if (visible && isInitialized && !handledEventInComposition) {
            currentOnValueChange(memo)
        }
        if (!visible) handledEventInComposition = false
    }

    if (!visible) return
    FormsInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            initial = memo,
            hint = stringResource(R.string.Send_DialogMemoHint),
            hintColor = ComposeAppTheme.colors.grey50,
            hintStyle = ComposeAppTheme.typography.bodyItalic,
            textColor = ComposeAppTheme.colors.leah,
            textStyle = ComposeAppTheme.typography.bodyItalic,
            pasteEnabled = false,
            singleLine = true,
            maxLength = maxLength,
            onValueChange = {
                memo = it
                isInitialized = true
                onValueChange(it)
            }
    )
}

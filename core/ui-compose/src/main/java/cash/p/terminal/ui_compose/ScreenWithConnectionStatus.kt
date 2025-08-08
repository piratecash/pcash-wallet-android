package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
fun ScreenWithoutConnectionPanel(
    content: @Composable () -> Unit
) {
    val connectionPanelState = LocalConnectionPanelState.current
    val originalValue = remember { connectionPanelState.value }

    LaunchedEffect(Unit) {
        connectionPanelState.value = false
    }

    DisposableEffect(Unit) {
        onDispose {
            connectionPanelState.value = originalValue
        }
    }

    content()
}
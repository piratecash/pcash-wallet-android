package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

private val INDICATOR_WIDTH = 8.dp
private val INDICATOR_RADIUS = 8.dp
private val INDICATOR_END_PADDING = 12.dp

@Composable
fun VerticalScrollIndicator(
    state: ScrollIndicatorState?,
    modifier: Modifier = Modifier,
) {
    val trackColor = ComposeAppTheme.colors.steel20
    val barColor = ComposeAppTheme.colors.grey

    Canvas(modifier = modifier.padding(end = INDICATOR_END_PADDING)) {
        val scrollState = state ?: return@Canvas
        val viewportSize = scrollState.viewportSize
        val contentSize = scrollState.contentSize
        val scrollOffset = scrollState.scrollOffset
        if (viewportSize <= 0 || contentSize <= viewportSize) return@Canvas
        if (contentSize == Int.MAX_VALUE || scrollOffset == Int.MAX_VALUE) return@Canvas

        val width = INDICATOR_WIDTH.toPx()
        val radius = CornerRadius(INDICATOR_RADIUS.toPx())
        val barHeight = (size.height * viewportSize / contentSize)
            .coerceAtLeast(width)
            .coerceAtMost(size.height)
        val barTravel = size.height - barHeight
        val scrollProgress = scrollOffset.toFloat() / (contentSize - viewportSize)
        val x = size.width - width

        drawRoundRect(trackColor, Offset(x, 0f), Size(width, size.height), radius)
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, barTravel * scrollProgress.coerceIn(0f, 1f)),
            size = Size(width, barHeight),
            cornerRadius = radius,
        )
    }
}

package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun HSpacer(width: Dp) {
    Spacer(modifier = Modifier.width(width))
}

@Composable
fun RowScope.HFillSpacer(minWidth: Dp) {
    Spacer(modifier = Modifier.width(minWidth))
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
fun VSpacer(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}

@Composable
fun ColumnScope.VFillSpacer(minHeight: Dp) {
    Spacer(modifier = Modifier.height(minHeight))
    Spacer(modifier = Modifier.weight(1f))
}

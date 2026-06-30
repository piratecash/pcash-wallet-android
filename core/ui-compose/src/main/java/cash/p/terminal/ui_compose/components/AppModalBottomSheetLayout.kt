package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

/**
 * Material 2 [ModalBottomSheetLayout] with the app-wide modal overlay scrim and a transparent
 * sheet background (the sheet content draws its own background). Keeps the unified overlay in a
 * single place instead of repeating it on every screen.
 *
 * Transitional: consolidates the scrim for legacy Material 2 screens only. New screens must use
 * the Material 3 [cash.p.terminal.ui_compose.TransparentModalBottomSheet].
 */
@Deprecated("Material 2 modal. New code must use the Material 3 TransparentModalBottomSheet.")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppModalBottomSheetLayout(
    sheetState: ModalBottomSheetState,
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetBackgroundColor = ComposeAppTheme.colors.transparent,
        scrimColor = ComposeAppTheme.colors.modalOverlay,
        sheetContent = sheetContent,
        content = content,
    )
}

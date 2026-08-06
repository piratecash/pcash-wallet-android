package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.R
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.BalanceHideOnFlipManager
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceHiddenInfoBottomSheet(
    onGotIt: () -> Unit,
    onDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BalanceHiddenInfoContent(
            onGotIt = { hideThen(onGotIt) },
            onDontShowAgain = { hideThen(onDontShowAgain) },
            onClose = { hideThen(onDismiss) },
        )
    }
}

/**
 * Hosts [BalanceHiddenInfoBottomSheet] for the flip-to-hide feature on any screen that shows the
 * balance. Observes the durable [BalanceHideOnFlipManager.pendingInfo] latch and shows the sheet
 * once a flip has hidden the balance, wiring "got it" / "don't show again" / dismiss. [canShow] lets
 * the host screen suppress the sheet while it is locked or already has its own modal open, so a flip
 * never stacks a second sheet over them.
 */
@Composable
fun FlipHiddenBalanceInfoHost(canShow: Boolean) {
    // No Koin graph in @Preview, so skip the manager lookup there.
    if (LocalInspectionMode.current) return
    val flipManager = remember { getKoinInstance<BalanceHideOnFlipManager>() }
    val showFlipInfo by flipManager.pendingInfo.collectAsStateWithLifecycle()
    if (showFlipInfo && canShow) {
        BalanceHiddenInfoBottomSheet(
            onGotIt = flipManager::consumeInfo,
            onDontShowAgain = flipManager::suppressInfo,
            onDismiss = flipManager::consumeInfo,
        )
    }
}

@Composable
private fun BalanceHiddenInfoContent(
    onGotIt: () -> Unit,
    onDontShowAgain: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_off_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.grey),
        title = stringResource(R.string.balance_hide_on_flip_info_title),
        onCloseClick = onClose,
    ) {
        body_leah(
            text = stringResource(R.string.balance_hide_on_flip_info_description),
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
        VSpacer(height = 20.dp)
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.Button_GotIt),
            onClick = onGotIt,
        )
        VSpacer(height = 12.dp)
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.balance_hide_on_flip_dont_show_again),
            onClick = onDontShowAgain,
        )
        VSpacer(height = 32.dp)
    }
}

@Preview
@Composable
private fun BalanceHiddenInfoContentPreview() {
    ComposeAppTheme {
        BalanceHiddenInfoContent(
            onGotIt = {},
            onDontShowAgain = {},
            onClose = {},
        )
    }
}

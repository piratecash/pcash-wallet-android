package cash.p.terminal.modules.offline

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.tokenQueryId
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Recovery sheet for an operation blocked by offline mode. Owns the toggle view model itself so a
 * call site only has to remember which wallet was tapped.
 */
@Composable
internal fun OfflineBlockedBottomSheet(
    wallet: Wallet,
    onWentOnline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: OfflineModeToggleViewModel = koinViewModel(
        key = "${wallet.account.id}:${wallet.tokenQueryId}",
    ) { parametersOf(wallet) }
    val uiState = viewModel.uiState
    val view = LocalView.current
    val wentOnline by rememberUpdatedState(onWentOnline)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            HudHelper.showErrorMessage(view, it)
            viewModel.errorShown()
        }
    }
    LaunchedEffect(uiState.closeSheet) {
        if (uiState.closeSheet) {
            viewModel.sheetClosed()
            wentOnline()
        }
    }

    OfflineBlockedBottomSheetContent(
        coinCode = wallet.coin.code,
        inProgress = uiState.inProgress,
        onGoOnline = viewModel::goOnline,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineBlockedBottomSheetContent(
    coinCode: String,
    inProgress: Boolean,
    onGoOnline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.icon_warning_2_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(R.string.offline_mode_blocked_title),
            onCloseClick = close,
        ) {
            subhead2_leah(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(R.string.offline_mode_blocked_description, coinCode),
            )
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(R.string.offline_mode_go_online),
                onClick = onGoOnline,
                enabled = !inProgress,
                loadingIndicator = inProgress,
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_Cancel),
                onClick = close,
                enabled = !inProgress,
            )
            VSpacer(32.dp)
        }
    }
}

@Preview
@Composable
private fun OfflineBlockedBottomSheetPreview() {
    ComposeAppTheme {
        OfflineBlockedBottomSheetContent(
            coinCode = "USDT",
            inProgress = false,
            onGoOnline = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "In progress")
@Composable
private fun OfflineBlockedBottomSheetInProgressPreview() {
    ComposeAppTheme {
        OfflineBlockedBottomSheetContent(
            coinCode = "ETH",
            inProgress = true,
            onGoOnline = {},
            onDismiss = {},
        )
    }
}

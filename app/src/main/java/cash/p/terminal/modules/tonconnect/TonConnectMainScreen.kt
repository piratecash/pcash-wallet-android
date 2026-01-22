package cash.p.terminal.modules.tonconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TonConnectMainScreen(
    viewModel: TonConnectListViewModel,
    navController: NavController, deepLinkUri: String?,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val invalidUrlBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    val uiState = viewModel.uiState

    val dAppRequestEntity = uiState.dAppRequestEntity
    LaunchedEffect(dAppRequestEntity) {
        if (dAppRequestEntity != null) {
            navController.slideFromBottom(R.id.tcNewFragment, dAppRequestEntity)
            viewModel.onDappRequestHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            delay(300)
            invalidUrlBottomSheetState.show()
            viewModel.onErrorHandled()
        }
    }

    LaunchedEffect(Unit) {
        if (deepLinkUri != null) {
            viewModel.setConnectionUri(deepLinkUri)
        }
    }

    val scannerTitle = stringResource(R.string.TonConnect_Title)

    ModalBottomSheetLayout(
        sheetState = invalidUrlBottomSheetState,
        sheetBackgroundColor = ComposeAppTheme.colors.transparent,
        sheetContent = {
            ConfirmationBottomSheet(
                title = stringResource(R.string.TonConnect_Title),
                text = stringResource(R.string.TonConnect_Error_InvalidUrl),
                iconPainter = painterResource(R.drawable.ic_ton_connect_24),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
                confirmText = stringResource(R.string.Button_TryAgain),
                cautionType = Caution.Type.Warning,
                cancelText = stringResource(R.string.Button_Cancel),
                onConfirm = {
                    coroutineScope.launch {
                        invalidUrlBottomSheetState.hide()
                        navController.openQrScanner(
                            title = scannerTitle,
                            showPasteButton = true
                        ) { scannedText ->
                            viewModel.setConnectionUri(scannedText)
                        }
                    }
                },
                onClose = {
                    coroutineScope.launch { invalidUrlBottomSheetState.hide() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.tyler,
            topBar = {
                AppBar(
                    title = stringResource(R.string.TonConnect_Title),
                    navigationIcon = {
                        HsBackButton(onClick = { navController.popBackStack() })
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(it)
                    .windowInsetsPadding(windowInsets)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val dapps = uiState.dapps
                    if (dapps.isEmpty()) {
                        ListEmptyView(
                            text = stringResource(R.string.WalletConnect_NoConnection),
                            icon = R.drawable.ic_ton_connect_24
                        )
                    } else {
                        TonConnectSessionList(
                            dapps = dapps,
                            onDelete = viewModel::disconnect
                        )
                    }
                }
                ButtonsGroupWithShade {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        title = stringResource(R.string.TonConnect_NewConnect),
                        onClick = {
                            navController.openQrScanner(
                                title = scannerTitle,
                                showPasteButton = true
                            ) { scannedText ->
                                viewModel.setConnectionUri(scannedText)
                            }
                        }
                    )
                }
            }
        }
    }
}

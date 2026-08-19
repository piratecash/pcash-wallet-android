package cash.p.terminal.modules.zcashconfigure

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.activity.addCallback
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.moneroconfigure.BirthdayHeightAppBar
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.RestoreHeightScreen
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class ZcashConfigureFragment : BaseComposeFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity?.onBackPressedDispatcher?.addCallback(this) {
            close(findNavController())
        }
    }

    @Composable
    override fun GetContent(navController: NavController) {
        val initialConfig = navController.getInput<Input>()?.initialConfig
        ZcashConfigureScreen(
            initialConfig = initialConfig,
            onCloseWithResult = { closeWithConfig(it, navController) },
            onCloseClick = { close(navController) }
        )
    }

    private fun closeWithConfig(config: TokenConfig, navController: NavController) {
        navController.setNavigationResultX(Result(config))
        navController.popBackStackSafely()
    }

    private fun close(navController: NavController) {
        navController.setNavigationResultX(Result(null))
        navController.popBackStackSafely()
    }

    @Parcelize
    data class Result(val config: TokenConfig?) : Parcelable

    @Parcelize
    data class Input(val initialConfig: TokenConfig?) : Parcelable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZcashConfigureScreen(
    onCloseClick: () -> Unit,
    onCloseWithResult: (TokenConfig) -> Unit,
    initialConfig: TokenConfig? = null,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    val viewModel: ZcashConfigureViewModel = koinViewModel()

    val uiState = viewModel.uiState
    var showSlowSyncWarning by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialConfig) {
        viewModel.setInitialConfig(initialConfig)
    }

    LaunchedEffect(uiState.closeWithResult) {
        val result = uiState.closeWithResult ?: return@LaunchedEffect
        viewModel.onClosed()
        keyboardController?.hide()
        onCloseWithResult.invoke(result)
    }

    if (showSlowSyncWarning) {
        TransparentModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                showSlowSyncWarning = false
            }
        ) {
            SlowSyncWarningBottomSheet(
                text = stringResource(R.string.Restore_ZCash_SlowSyncWarningText),
                onContinueClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showSlowSyncWarning = false
                        }
                    }
                    viewModel.onModeSelect(RestoreHeightMode.ExistingWallet)
                },
                onCloseClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showSlowSyncWarning = false
                        }
                    }
                },
            )
        }
    }

    if (showDatePicker) {
        SelectDateBottomSheet(
            initialDateMillis = null,
            minDateMillis = restoreGenesisDateMillis(BlockchainType.Zcash),
            maxDateMillis = restoreMaxDateMillis(),
            onDateSelect = viewModel::onDatePicked,
            onDismiss = { showDatePicker = false }
        )
    }

    RestoreHeightScreen(
        mode = uiState.mode,
        onModeSelect = { mode ->
            when (mode) {
                RestoreHeightMode.NewWallet -> viewModel.onModeSelect(mode)
                RestoreHeightMode.ExistingWallet -> showSlowSyncWarning = true
            }
            focusManager.clearFocus()
        },
        doneEnabled = uiState.doneEnabled,
        onDoneClick = viewModel::onDoneClick,
        topBar = {
            BirthdayHeightAppBar(
                title = stringResource(R.string.Restore_ZCash),
                blockchainType = BlockchainType.Zcash,
                onCloseClick = onCloseClick,
            )
        },
        loading = uiState.loading,
        contentWindowInsets = windowInsets,
        existingWalletContent = {
            Spacer(Modifier.height(24.dp))
            HeaderText(text = stringResource(R.string.restore_birthday_height_or_date))

            RestoreHeightInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.birthdayHeight,
                hint = stringResource(R.string.restoreheight_hint),
                error = uiState.errorHeight,
                pasteEnabled = false,
                onValueChange = viewModel::setBirthdayHeight,
                onCalendarClick = { showDatePicker = true },
            )

            InfoText(
                text = stringResource(R.string.Restore_ZCash_BirthdayHeight_Hint),
            )
        },
        additionalContent = {
            Spacer(Modifier.height(24.dp))
        },
    )
}

@Composable
private fun SlowSyncWarningBottomSheet(
    text: String,
    onContinueClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_attention_24),
        title = stringResource(R.string.Alert_TitleWarning),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        onCloseClick = onCloseClick
    ) {
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = text
        )

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            title = stringResource(id = R.string.Button_Continue),
            onClick = onContinueClick
        )

        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            title = stringResource(id = R.string.Button_Cancel),
            onClick = onCloseClick
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Preview
@Composable
private fun Preview_ZcashConfigure() {
    ComposeAppTheme(darkTheme = false) {
        ZcashConfigureScreen(
            onCloseClick = {},
            onCloseWithResult = {},
            initialConfig = null
        )
    }
}

package cash.p.terminal.modules.balance.token.creationblock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogBottomSheet
import java.time.LocalDate

@Composable
internal fun CreationBlockScreen(
    uiState: CreationBlockUiState,
    onHeightChange: (String) -> Unit,
    onDatePick: (LocalDate) -> Unit,
    onRescanConfirm: () -> Unit,
    onClose: () -> Unit,
    onRescanComplete: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showRescanConfirm by remember { mutableStateOf(false) }
    val currentOnRescanComplete by rememberUpdatedState(onRescanComplete)

    LaunchedEffect(uiState.rescanStarted) {
        if (uiState.rescanStarted) {
            currentOnRescanComplete()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Restore_BirthdayHeight),
                navigationIcon = { HsBackButton(onClick = onClose) }
            )
        }
    ) { paddingValues ->
        CreationBlockContent(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onHeightChange = onHeightChange,
            onCalendarClick = { showDatePicker = true },
            onRescanClick = { showRescanConfirm = true },
        )
    }

    if (showDatePicker) {
        SelectDateBottomSheet(
            initialDateMillis = null,
            minDateMillis = restoreGenesisDateMillis(uiState.blockchainType),
            maxDateMillis = restoreMaxDateMillis(),
            onDateSelect = onDatePick,
            onDismiss = { showDatePicker = false },
        )
    }

    if (showRescanConfirm) {
        ConfirmationDialogBottomSheet(
            title = stringResource(R.string.birthday_height_rescan_confirm_title),
            icon = null,
            warningTitle = null,
            warningText = stringResource(R.string.birthday_height_rescan_confirm_text),
            actionButtonTitle = stringResource(R.string.birthday_height_rescan),
            transparentButtonTitle = stringResource(R.string.Button_Cancel),
            onCloseClick = { showRescanConfirm = false },
            onActionButtonClick = {
                showRescanConfirm = false
                onRescanConfirm()
            },
            onTransparentButtonClick = { showRescanConfirm = false },
        )
    }
}

@Composable
private fun CreationBlockContent(
    uiState: CreationBlockUiState,
    onHeightChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)
            InfoText(text = stringResource(R.string.birthday_height_description))
            VSpacer(12.dp)
            RestoreHeightInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.heightText,
                hint = stringResource(R.string.restoreheight_hint_block_only),
                error = uiState.error,
                pasteEnabled = true,
                onValueChange = onHeightChange,
                onCalendarClick = onCalendarClick,
                numericOnly = true,
            )
            VSpacer(12.dp)
            BlockDateRow(dateText = uiState.blockDateText)
        }
        ButtonsGroupWithShade {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.birthday_height_rescan),
                enabled = uiState.changed && !uiState.loading,
                loadingIndicator = uiState.loading,
                onClick = onRescanClick,
            )
        }
    }
}

@Composable
private fun BlockDateRow(dateText: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        subhead2_grey(text = stringResource(R.string.birthday_height_date_label))
        subhead1_leah(text = dateText ?: "—")
    }
}

@Preview
@Composable
internal fun CreationBlockScreenPreview() {
    ComposeAppTheme {
        CreationBlockScreen(
            uiState = CreationBlockUiState(
                blockchainType = BlockchainType.Monero,
                heightText = "2975499",
                blockDateText = "9 августа 2024 г.",
                changed = true,
            ),
            onHeightChange = {},
            onDatePick = {},
            onRescanConfirm = {},
            onClose = {},
            onRescanComplete = {},
        )
    }
}

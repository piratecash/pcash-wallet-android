package cash.p.terminal.modules.calculator.pinsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.calculator.domain.CalculatorAutoLockOption
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.AppCloseWarningBottomSheet
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalculatorPinSettingsScreen(
    uiState: CalculatorPinSettingsUiState,
    onToggleCalculator: (Boolean) -> Unit,
    onAutoLockClick: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pendingToggle by remember { mutableStateOf<Boolean?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    pendingToggle?.let { newValue ->
        AppCloseWarningBottomSheet(
            sheetState = sheetState,
            onDismiss = { pendingToggle = null },
            onConfirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    pendingToggle = null
                    onToggleCalculator(newValue)
                }
            },
        )
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.calculator_pin_settings_title),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            VSpacer(12.dp)
            SettingsSection(
                isEnabled = uiState.isEnabled,
                autoLockTitle = uiState.autoLockOption.formatShort(),
                onToggleRequest = { newValue -> pendingToggle = newValue },
                onAutoLockClick = onAutoLockClick,
            )
            Description()
        }
    }
}

@Composable
private fun SettingsSection(
    isEnabled: Boolean,
    autoLockTitle: String,
    onToggleRequest: (Boolean) -> Unit,
    onAutoLockClick: () -> Unit,
) {
    CellUniversalLawrenceSection(
        listOf(
            {
                RowUniversal(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_calculator),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.grey,
                    )
                    body_leah(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        text = stringResource(R.string.calculator_mode),
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    HsSwitch(
                        checked = isEnabled,
                        onCheckedChange = onToggleRequest,
                    )
                }
            },
            {
                HsSettingCell(
                    title = R.string.calculator_auto_lock_title,
                    icon = R.drawable.ic_lock_20,
                    value = autoLockTitle,
                    onClick = onAutoLockClick,
                )
            }
        )
    )
}

@Composable
private fun Description() {
    Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        subhead2_grey(text = stringResource(R.string.calculator_pin_settings_intro))
        VSpacer(12.dp)
        subhead2_grey(text = stringResource(R.string.calculator_pin_settings_unlock_methods_title))
        BulletItem(stringResource(R.string.calculator_pin_settings_unlock_method_pin))
        BulletItem(stringResource(R.string.calculator_pin_settings_unlock_method_expression))
        BulletItem(stringResource(R.string.calculator_pin_settings_leading_zeros))
        VSpacer(12.dp)
        subhead2_grey(text = stringResource(R.string.calculator_pin_settings_throttle_invisible))
        VSpacer(12.dp)
        subhead2_grey(text = stringResource(R.string.calculator_pin_settings_outro))
    }
}

@Composable
private fun BulletItem(text: String) {
    Row {
        subhead2_grey(text = "•  ")
        subhead2_grey(text = text, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun CalculatorPinSettingsScreenPreview() {
    ComposeAppTheme {
        CalculatorPinSettingsScreen(
            uiState = CalculatorPinSettingsUiState(
                isEnabled = true,
                autoLockOption = CalculatorAutoLockOption.DEFAULT,
            ),
            onToggleCalculator = {},
            onAutoLockClick = {},
            onClose = {},
        )
    }
}

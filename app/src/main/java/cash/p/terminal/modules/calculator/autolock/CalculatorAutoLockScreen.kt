package cash.p.terminal.modules.calculator.autolock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.calculator.domain.CalculatorAutoLockOption
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun CalculatorAutoLockScreen(
    uiState: CalculatorAutoLockUiState,
    onSelect: (CalculatorAutoLockOption) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.calculator_auto_lock_title),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)
            CellUniversalLawrenceSection(CalculatorAutoLockOption.entries) { option ->
                IntervalCell(
                    option = option,
                    checked = option == uiState.selected,
                    onClick = onSelect,
                )
            }
        }
    }
}

@Composable
private fun IntervalCell(
    option: CalculatorAutoLockOption,
    checked: Boolean,
    onClick: (CalculatorAutoLockOption) -> Unit,
) {
    RowUniversal(onClick = { onClick(option) }) {
        HSpacer(16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = option.formatLong(),
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.jacob,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun CalculatorAutoLockScreenPreview() {
    ComposeAppTheme {
        CalculatorAutoLockScreen(
            uiState = CalculatorAutoLockUiState(selected = CalculatorAutoLockOption.AFTER_30_SEC),
            onSelect = {},
            onClose = {},
        )
    }
}

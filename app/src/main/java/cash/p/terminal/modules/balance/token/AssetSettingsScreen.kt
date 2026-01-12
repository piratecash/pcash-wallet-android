package cash.p.terminal.modules.balance.token

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.transactions.AmlCheckInfoBottomSheet
import cash.p.terminal.modules.transactions.AmlCheckRow
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun AssetSettingsScreen(
    amlCheckEnabled: Boolean,
    onAmlCheckChange: (Boolean) -> Unit,
    navController: NavController,
    onBack: () -> Unit
) {
    var showAmlInfoSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Settings_Title),
                navigationIcon = { HsBackButton(onClick = onBack) }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            VSpacer(12.dp)
            AmlCheckRow(
                enabled = amlCheckEnabled,
                onToggleChange = onAmlCheckChange,
                onInfoClick = { showAmlInfoSheet = true }
            )
        }
    }

    if (showAmlInfoSheet) {
        AmlCheckInfoBottomSheet(
            onPremiumSettingsClick = {
                showAmlInfoSheet = false
                navController.slideFromRight(R.id.premiumSettingsFragment)
            },
            onLaterClick = { showAmlInfoSheet = false },
            onDismiss = { showAmlInfoSheet = false }
        )
    }
}

@Composable
fun AssetSettingsScreen(
    uiState: AssetSettingsUiState,
    onAmlCheckChange: (Boolean) -> Unit,
    onAmlInfoClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Settings_Title),
                navigationIcon = { HsBackButton(onClick = onBack) }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            VSpacer(12.dp)
            AmlCheckRow(
                enabled = uiState.amlCheckEnabled,
                onToggleChange = onAmlCheckChange,
                onInfoClick = onAmlInfoClick
            )
        }
    }
}

data class AssetSettingsUiState(
    val amlCheckEnabled: Boolean = false
)

@Preview
@Composable
private fun AssetSettingsScreenPreview() {
    ComposeAppTheme {
        AssetSettingsScreen(
            uiState = AssetSettingsUiState(amlCheckEnabled = true),
            onAmlCheckChange = {},
            onAmlInfoClick = {},
            onBack = {}
        )
    }
}

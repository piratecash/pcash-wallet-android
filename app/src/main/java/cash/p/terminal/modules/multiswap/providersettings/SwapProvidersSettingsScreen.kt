package cash.p.terminal.modules.multiswap.providersettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun SwapProvidersSettingsScreen(
    uiState: SwapProvidersSettingsUiState,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    val mandatoryToast = stringResource(R.string.swap_providers_mandatory_toast)

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.swap_providers_title),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            SectionUniversalLawrence {
                Column {
                    uiState.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HsDivider()
                        }
                        ProviderRow(
                            item = item,
                            onToggle = { enabled ->
                                if (item.mandatory) {
                                    HudHelper.showErrorMessage(view, mandatoryToast)
                                } else {
                                    onToggle(item.id, enabled)
                                }
                            },
                            onMandatoryClick = {
                                HudHelper.showErrorMessage(view, mandatoryToast)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    item: SwapProviderItem,
    onToggle: (Boolean) -> Unit,
    onMandatoryClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = if (item.mandatory) onMandatoryClick else null,
    ) {
        Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(item.icon),
            contentDescription = null,
        )
        HSpacer(16.dp)
        if (item.enabled) {
            body_leah(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            body_grey50(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HsSwitch(
            checked = item.enabled,
            enabled = !item.mandatory,
            onCheckedChange = onToggle,
        )
    }
}

@Preview
@Composable
private fun SwapProvidersSettingsScreenPreview() {
    ComposeAppTheme {
        SwapProvidersSettingsScreen(
            uiState = SwapProvidersSettingsUiState(
                items = listOf(
                    SwapProviderItem("uniswap", "Uniswap", R.drawable.uniswap, enabled = true, mandatory = false),
                    SwapProviderItem("pancake", "PancakeSwap", R.drawable.uniswap, enabled = true, mandatory = true),
                    SwapProviderItem("oneinch", "1inch", R.drawable.uniswap, enabled = false, mandatory = false),
                )
            ),
            onToggle = { _, _ -> },
            onClose = {},
        )
    }
}

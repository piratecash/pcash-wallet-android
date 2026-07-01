package cash.p.terminal.modules.multiswap.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.modules.multiswap.SwapViewModel
import cash.p.terminal.modules.paycore.selectbank.PayCoreBankSwapSetting
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.SearchField
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SwapSettingsScreen(
    settingsNavController: NavController,
    appNavController: NavController,
    swapViewModel: SwapViewModel
) {
    val viewModel =
        viewModel<SwapSettingsViewModel>(factory = SwapSettingsViewModel.Factory(swapViewModel.getSettings()))

    val uiState = viewModel.uiState
    val settings = swapViewModel.uiState.quote?.swapQuote?.settings
    val customTitle = settings?.takeIf { it.size == 1 }?.firstOrNull()?.titleRes
    val bankSetting = settings?.firstNotNullOfOrNull { it as? PayCoreBankSwapSetting }
    val keyboardController = LocalSoftwareKeyboardController.current
    var bankSearchQuery by remember { mutableStateOf("") }

    val applySettings: () -> Unit = {
        keyboardController?.hide()
        swapViewModel.onUpdateSettings(viewModel.getSettings())
        settingsNavController.popBackStackSafely()
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(customTitle ?: R.string.SwapSettings_Title),
                navigationIcon = {
                    HsBackButton(onClick = settingsNavController::popBackStackSafely)
                },
                menuItems = if (bankSetting != null) {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.SwapSettings_Apply),
                            icon = R.drawable.ic_checkmark_20,
                            enabled = uiState.applyEnabled,
                            onClick = applySettings,
                        )
                    )
                } else {
                    listOf()
                },
            )
        },
        bottomBar = {
            ButtonsGroupWithShade(modifier = Modifier.navigationBarsPadding()) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    title = stringResource(id = R.string.SwapSettings_Apply),
                    enabled = uiState.applyEnabled,
                    onClick = applySettings
                )
            }
        },
        containerColor = ComposeAppTheme.colors.tyler,
    ) {
        Column(modifier = Modifier.padding(it)) {
            if (bankSetting?.hasBanks == true) {
                SearchField(
                    onSearchTextChanged = { query -> bankSearchQuery = query },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    VSpacer(height = 12.dp)
                }

                settings?.forEach { setting ->
                    val settingId = setting.id
                    val onError: (Throwable?) -> Unit = { viewModel.onSettingError(settingId, it) }
                    val onValueChange: (Any?) -> Unit = { viewModel.onSettingEnter(settingId, it) }

                    if (setting is PayCoreBankSwapSetting) {
                        with(setting) {
                            addBankItems(
                                value = uiState.settings[settingId],
                                query = bankSearchQuery,
                                onError = onError,
                                onValueChange = onValueChange,
                            )
                        }
                    } else {
                        with(setting) {
                            addContentItems(
                                navController = appNavController,
                                value = uiState.settings[settingId],
                                onError = onError,
                                onValueChange = onValueChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

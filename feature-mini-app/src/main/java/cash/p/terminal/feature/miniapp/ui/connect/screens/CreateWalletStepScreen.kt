package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.connect.WalletViewItem
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.ui_compose.components.BackupButtons
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun CreateWalletStepScreen(
    isLoading: Boolean,
    needsBackup: Boolean,
    onNewWalletClick: () -> Unit,
    onImportWalletClick: () -> Unit,
    onManualBackupClick: () -> Unit,
    onLocalBackupClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_1),
        stepDescription = stringResource(R.string.connect_mini_app_step_1_description),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        isLoading = isLoading,
        scrollable = false,
        modifier = modifier,
        content = {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)) {
                Spacer(Modifier.weight(1f))
                if (needsBackup) {
                    BackupButtons(
                        onManualBackupClick = onManualBackupClick,
                        onLocalBackupClick = onLocalBackupClick
                    )
                } else {
                    ButtonPrimaryYellow(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.ManageAccounts_CreateNewWallet),
                        onClick = onNewWalletClick
                    )

                    VSpacer(16.dp)

                    ButtonPrimaryDefault(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.ManageAccounts_ImportWallet),
                        onClick = onImportWalletClick
                    )
                }
                Spacer(Modifier.weight(2f))
            }
        }
    )
}

@Composable
fun WalletSelectionScreen(
    isLoading: Boolean,
    walletItems: List<WalletViewItem>,
    selectedAccountId: String?,
    onWalletSelected: (String) -> Unit,
    onContinueClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_1),
        stepDescription = stringResource(R.string.connect_mini_app_step_1_choose_wallet),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        isLoading = isLoading,
        modifier = modifier,
        bottomContent = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Continue),
                onClick = onContinueClick,
                enabled = selectedAccountId != null
            )
        },
        content = {
            VSpacer(16.dp)

            CellUniversalLawrenceSection(items = walletItems) { item ->
                RowUniversal(
                    onClick = { onWalletSelected(item.accountId) }
                ) {
                    HsRadioButton(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        selected = item.accountId == selectedAccountId,
                        onClick = { onWalletSelected(item.accountId) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        body_leah(text = item.name)
                        if (item.isPremium) {
                            subhead2_jacob(text = stringResource(R.string.connect_mini_app_premium_bonus))
                        }
                    }
                    if (item.isPremium) {
                        Icon(
                            painter = painterResource(R.drawable.star_filled_yellow_16),
                            tint = ComposeAppTheme.colors.jacob,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WalletSelectionScreenPreview() {
    ComposeAppTheme {
        WalletSelectionScreen(
            isLoading = false,
            walletItems = listOf(
                WalletViewItem("1", "Wallet 1", false),
                WalletViewItem("2", "Wallet 2", false),
                WalletViewItem("3", "Wallet 3", true),
                WalletViewItem("4", "Wallet 4", false),
            ),
            selectedAccountId = "3",
            onWalletSelected = {},
            onContinueClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateWalletStepScreenNormalPreview() {
    ComposeAppTheme {
        CreateWalletStepScreen(
            isLoading = false,
            needsBackup = false,
            onNewWalletClick = {},
            onImportWalletClick = {},
            onManualBackupClick = {},
            onLocalBackupClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateWalletStepScreenBackupPreview() {
    ComposeAppTheme {
        CreateWalletStepScreen(
            isLoading = false,
            needsBackup = true,
            onNewWalletClick = {},
            onImportWalletClick = {},
            onManualBackupClick = {},
            onLocalBackupClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1)
        )
    }
}

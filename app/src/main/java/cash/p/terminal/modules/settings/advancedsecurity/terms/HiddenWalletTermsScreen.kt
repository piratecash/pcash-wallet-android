package cash.p.terminal.modules.settings.advancedsecurity.terms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TermsList
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun HiddenWalletTermsScreen(
    uiState: HiddenWalletTermsUiState,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.AdvancedSecurity_Terms_Title),
                navigationIcon = {
                    HsBackButton(onClick = onNavigateBack)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.AdvancedSecurity_Terms_Warning)
                )
                VSpacer(12.dp)
                TermsList(
                    terms = uiState.terms,
                    modifier = Modifier
                        .weight(1f),
                    onItemClicked = onCheckboxToggle
                )
                VSpacer(32.dp)
            }
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = if (uiState.allAcceptedBefore) {
                        stringResource(R.string.create_hidden_wallet)
                    } else {
                        stringResource(R.string.Button_IAgree)
                    },
                    onClick = onAgreeClick,
                    enabled = uiState.agreeEnabled
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HiddenWalletTermsScreenPreview() {
    val termTitles = LocalContext.current.resources.getStringArray(R.array.AdvancedSecurity_Terms_Checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }
    ComposeAppTheme {
        HiddenWalletTermsScreen(
            uiState = HiddenWalletTermsUiState(
                terms = terms,
                agreeEnabled = false,
                allAcceptedBefore = true
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onNavigateBack = {}
        )
    }
}

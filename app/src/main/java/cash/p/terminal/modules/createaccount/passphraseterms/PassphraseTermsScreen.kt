package cash.p.terminal.modules.createaccount.passphraseterms

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
fun PassphraseTermsScreen(
    uiState: PassphraseTermsUiState,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.passphrase_terms_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
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
                if (!uiState.alreadyAgreed) {
                    VSpacer(12.dp)
                    TextImportantWarning(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = stringResource(R.string.passphrase_terms_warning)
                    )
                }
                VSpacer(12.dp)
                TermsList(
                    terms = uiState.terms,
                    onItemClicked = onCheckboxToggle
                )
            }
            if (!uiState.alreadyAgreed) {
                ButtonsGroupWithShade {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        title = stringResource(R.string.Button_IAgree),
                        enabled = uiState.agreeEnabled,
                        onClick = onAgreeClick,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PassphraseTermsScreenPreview() {
    val termTitles = LocalContext.current.resources.getStringArray(R.array.passphrase_terms_checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }
    ComposeAppTheme {
        PassphraseTermsScreen(
            uiState = PassphraseTermsUiState(
                terms = terms,
                agreeEnabled = false,
                alreadyAgreed = false
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onBackClick = {}
        )
    }
}

@Preview
@Composable
private fun PassphraseTermsScreenAlreadyAgreedPreview() {
    val termTitles = LocalContext.current.resources.getStringArray(R.array.passphrase_terms_checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = true
        )
    }
    ComposeAppTheme {
        PassphraseTermsScreen(
            uiState = PassphraseTermsUiState(
                terms = terms,
                agreeEnabled = true,
                alreadyAgreed = true
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onBackClick = {}
        )
    }
}

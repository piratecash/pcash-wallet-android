package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.createaccount.PassphraseCell
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun DuplicateWalletScreen(
    uiState: DuplicateWalletUiState,
    passphraseTermsAccepted: Boolean,
    onEnterName: (String) -> Unit,
    onTogglePassphrase: (Boolean) -> Unit,
    onChangePassphrase: (String) -> Unit,
    onChangePassphraseConfirmation: (String) -> Unit,
    onCreate: () -> Unit,
    onBackClick: () -> Unit,
    onOpenTerms: () -> Unit,
    onFinish: () -> Unit
) {
    val view = LocalView.current

    LaunchedEffect(uiState.closeScreen) {
        if (uiState.closeScreen) {
            HudHelper.showSuccessMessage(view, R.string.wallet_duplicated, SnackbarDuration.MEDIUM)
            onFinish()
        }
    }
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.duplicate_wallet),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.create),
                        onClick = onCreate,
                        enabled = uiState.createButtonEnabled
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            val state = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(state)
            ) {
                Spacer(Modifier.height(12.dp))

                HeaderText(stringResource(id = R.string.ManageAccount_Name))
                FormsInput(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    initial = uiState.accountName,
                    pasteEnabled = false,
                    hint = stringResource(R.string.ManageAccount_Name),
                    onValueChange = onEnterName
                )

                uiState.error?.let { errorText ->
                    Spacer(Modifier.height(16.dp))
                    caption_lucian(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = errorText
                    )
                }

                if (uiState.passphraseAvailable) {
                    Spacer(Modifier.height(32.dp))

                    if (passphraseTermsAccepted) {
                        CellUniversalLawrenceSection(
                            listOf {
                                RowUniversal(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = onOpenTerms
                                ) {
                                    body_leah(
                                        text = stringResource(R.string.passphrase_terms_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Image(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = R.drawable.ic_arrow_right),
                                        contentDescription = null,
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    CellUniversalLawrenceSection(listOf {
                        PassphraseCell(
                            enabled = uiState.passphraseEnabled,
                            onCheckedChange = { enabled ->
                                if (!passphraseTermsAccepted && enabled) {
                                    onOpenTerms()
                                } else {
                                    onTogglePassphrase(enabled)
                                }
                            }
                        )
                    })

                    if (uiState.passphraseEnabled) {
                        var hidePassphrase by remember { mutableStateOf(true) }
                        val textStatePassword =
                            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                                mutableStateOf(TextFieldValue(uiState.passcodeOld))
                            }
                        Spacer(Modifier.height(24.dp))
                        FormsInputPassword(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            hint = stringResource(R.string.Passphrase),
                            state = uiState.passphraseState,
                            onValueChange = onChangePassphrase,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            hide = hidePassphrase,
                            textState = textStatePassword,
                            onToggleHide = {
                                hidePassphrase = !hidePassphrase
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        val textStatePasswordConfirm =
                            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                                mutableStateOf(TextFieldValue(uiState.passcodeOld))
                            }
                        FormsInputPassword(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            hint = stringResource(R.string.ConfirmPassphrase),
                            state = uiState.passphraseConfirmState,
                            onValueChange = onChangePassphraseConfirmation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            hide = hidePassphrase,
                            textState = textStatePasswordConfirm,
                            onToggleHide = {
                                hidePassphrase = !hidePassphrase
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        D1(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            text = stringResource(R.string.CreateWallet_PassphraseDescription)
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Preview
@Composable
private fun DuplicateWalletScreenPreview() {
    ComposeAppTheme {
        DuplicateWalletScreen(
            uiState = DuplicateWalletUiState(
                accountName = "My Wallet",
                passphraseEnabled = true,
                passphraseAvailable = true,
                passcodeOld = "",
            ),
            passphraseTermsAccepted = true,
            onEnterName = {},
            onTogglePassphrase = {},
            onChangePassphrase = {},
            onChangePassphraseConfirmation = {},
            onCreate = {},
            onBackClick = {},
            onOpenTerms = {},
            onFinish = {}
        )
    }
}

package cash.p.terminal.modules.premium.smsnotification

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.openQrScanner
import cash.p.terminal.entities.Address
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInputAddress
import cash.p.terminal.ui.extensions.WalletSwitchBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.title
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

@Composable
fun SendSmsNotificationScreen(
    navController: NavController,
    uiState: SendSmsNotificationUiState,
    onAccountSelected: (Account) -> Unit,
    onAddressChanged: (String) -> Unit,
    onMemoChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onTestSmsClick: () -> Unit,
    onSaveSuccessShown: () -> Unit,
    onTestResultShown: () -> Unit,
    onCancelTest: () -> Unit,
    onClose: () -> Unit
) {
    var showInfoSheet by remember { mutableStateOf(false) }
    var showWalletSheet by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scannerTitle = stringResource(R.string.qr_scanner_title_address, BlockchainType.Zcash.title)

    // Handle back press - cancel test if syncing
    BackHandler {
        if (uiState.testResult is TestResult.Syncing) {
            onCancelTest()
        }
        onClose()
    }

    // Handle save success
    if (uiState.saveSuccess) {
        HudHelper.showSuccessMessage(
            contenView = view,
            resId = R.string.Hud_Text_Done
        )
        onSaveSuccessShown()
    }

    // Handle test result
    uiState.testResult?.let { result ->
        when (result) {
            is TestResult.Success -> {
                HudHelper.showSuccessMessage(
                    contenView = view,
                    resId = R.string.send_sms_test_success,
                    duration = SnackbarDuration.LONG
                )
            }

            is TestResult.InsufficientBalance -> {
                HudHelper.showErrorMessage(
                    contenView = view,
                    resId = R.string.send_sms_insufficient_balance
                )
            }

            is TestResult.Failed -> {
                HudHelper.showErrorMessage(
                    contenView = view,
                    resId = R.string.send_sms_test_error
                )
            }

            is TestResult.Syncing -> {
                // Show loading state on button
            }
        }
        if (result !is TestResult.Syncing) {
            onTestResultShown()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.send_sms_via_zec_title),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.button_info),
                        icon = R.drawable.ic_info_20,
                        onClick = { showInfoSheet = true }
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(height = 12.dp)

            // Choose Wallet Section
            CellUniversalLawrenceSection(
                listOf {
                    RowUniversal(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = { showWalletSheet = true }
                    ) {
                        body_leah(text = stringResource(R.string.choose_wallet))
                        Spacer(modifier = Modifier.weight(1f))
                        subhead1_grey(
                            text = uiState.selectedWallet?.accountName
                                ?: stringResource(R.string.choose_wallet_none)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_down_arrow_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.grey
                        )
                    }
                }
            )

            VSpacer(height = 12.dp)

            // Address hint text
            InfoText(
                text = stringResource(R.string.send_sms_address_hint)
            )

            VSpacer(height = 12.dp)

            // Address Input
            FormsInputAddress(
                modifier = Modifier.padding(horizontal = 16.dp),
                value = uiState.address,
                hint = stringResource(R.string.Send_Hint_Address),
                state = when {
                    uiState.addressError != null -> DataState.Error(uiState.addressError)
                    uiState.address.isNotBlank() -> DataState.Success(Address(uiState.address))
                    else -> null
                },
                navController = navController,
                chooseContactEnable = false,
                blockchainType = BlockchainType.Zcash,
                onQrScanClick = {
                    navController.openQrScanner(scannerTitle) { scannedText ->
                        onAddressChanged(scannedText)
                    }
                },
                onValueChange = onAddressChanged
            )

            VSpacer(height = 24.dp)

            // Memo Input
            MemoInputField(
                value = uiState.memo,
                onValueChange = onMemoChanged
            )

            // Byte counter
            caption_grey(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                text = "${uiState.memoBytesUsed}/${SendSmsNotificationUiState.MAX_MEMO_BYTES}",
                textAlign = TextAlign.End
            )

            VSpacer(height = 8.dp)

            // Required coins for transaction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                subhead2_grey(text = stringResource(R.string.required_coins_for_transaction))
                Spacer(modifier = Modifier.weight(1f))
                subhead2_leah(
                    text = "${formatAmount(uiState.requiredCoins)} ZEC"
                )
            }

            VSpacer(height = 12.dp)

            TextImportantWarning(
                text = stringResource(R.string.send_sms_wallet_balance_hint),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            VSpacer(height = 24.dp)

            // Save Button
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.Button_Save),
                onClick = onSaveClick,
                enabled = uiState.isSaveEnabled
            )

            VSpacer(height = 16.dp)

            // Test SMS Button
            val isSyncing = uiState.testResult is TestResult.Syncing
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = if (isSyncing) {
                    stringResource(R.string.send_sms_syncing)
                } else {
                    stringResource(R.string.test_sms)
                },
                onClick = onTestSmsClick,
                enabled = uiState.isTestEnabled && !isSyncing
            )

            VSpacer(height = 32.dp)
        }
    }

    // Wallet Selection Bottom Sheet
    if (showWalletSheet) {
        WalletSwitchBottomSheet(
            wallets = uiState.availableAccounts.filter { !it.isWatchAccount },
            watchingAddresses = uiState.availableAccounts.filter { it.isWatchAccount },
            selectedAccount = uiState.selectedAccount,
            onSelectListener = onAccountSelected,
            onDismiss = { showWalletSheet = false },
            title = stringResource(R.string.choose_wallet)
        )
    }

    // Info Bottom Sheet
    if (showInfoSheet) {
        InfoBottomSheet(
            title = stringResource(R.string.send_sms_via_zec_title),
            text = stringResource(R.string.send_sms_via_zec_info),
            onDismiss = { showInfoSheet = false }
        )
    }
}

@Composable
private fun MemoInputField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.steel20, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .weight(1f),
            value = value,
            onValueChange = onValueChange,
            textStyle = ComposeAppTheme.typography.bodyItalic.copy(
                color = ComposeAppTheme.colors.leah
            ),
            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        body_grey50(text = stringResource(R.string.Send_DialogMemoHint))
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun formatAmount(amount: BigDecimal): String {
    return if (amount == BigDecimal.ZERO) {
        "0"
    } else {
        amount.stripTrailingZeros().toPlainString()
    }
}

@Preview(showBackground = true)
@Composable
private fun SendSmsNotificationScreenEmptyPreview() {
    ComposeAppTheme {
        SendSmsNotificationScreen(
            navController = rememberNavController(),
            uiState = SendSmsNotificationUiState(),
            onAccountSelected = {},
            onAddressChanged = {},
            onMemoChanged = {},
            onSaveClick = {},
            onTestSmsClick = {},
            onSaveSuccessShown = {},
            onTestResultShown = {},
            onCancelTest = {},
            onClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendSmsNotificationScreenWithAddressPreview() {
    ComposeAppTheme {
        SendSmsNotificationScreen(
            navController = rememberNavController(),
            uiState = SendSmsNotificationUiState(
                address = "zs1example1234567890abcdefghijklmnop",
                memo = "Emergency notification",
                memoBytesUsed = 22,
                requiredCoins = BigDecimal("0.0001"),
                isSaveEnabled = true,
                isTestEnabled = true
            ),
            onAccountSelected = {},
            onAddressChanged = {},
            onMemoChanged = {},
            onSaveClick = {},
            onTestSmsClick = {},
            onSaveSuccessShown = {},
            onTestResultShown = {},
            onCancelTest = {},
            onClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendSmsNotificationScreenWithErrorPreview() {
    ComposeAppTheme {
        SendSmsNotificationScreen(
            navController = rememberNavController(),
            uiState = SendSmsNotificationUiState(
                address = "invalid_address",
                addressError = Exception("Invalid Zcash address"),
                requiredCoins = BigDecimal("0.0001")
            ),
            onAccountSelected = {},
            onAddressChanged = {},
            onMemoChanged = {},
            onSaveClick = {},
            onTestSmsClick = {},
            onSaveSuccessShown = {},
            onTestResultShown = {},
            onCancelTest = {},
            onClose = {}
        )
    }
}

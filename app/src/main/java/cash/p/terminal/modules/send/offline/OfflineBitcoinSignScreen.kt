package cash.p.terminal.modules.send.offline

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.bitcoin.OfflineSignState
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.annotatedHtmlStringResource
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.CustomSnackbar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.CurrencyValue

@Composable
internal fun OfflineBitcoinSignScreen(
    confirmationData: SendConfirmationData,
    blockchainName: String,
    coinMaxAllowedDecimals: Int,
    rate: CurrencyValue?,
    signState: OfflineSignState,
    callbacks: OfflineBitcoinSignCallbacks,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    var selectedFormat by rememberSaveable { mutableStateOf(OfflineTransactionFormat.Pcash) }
    var infoFormat by remember { mutableStateOf<OfflineTransactionFormat?>(null) }
    var currentSnackbar by remember { mutableStateOf<CustomSnackbar?>(null) }
    val contentState = OfflineBitcoinSignContentState(
        summaryState = confirmationData.toOfflineSignSummaryState(
            blockchainName = blockchainName,
            coinMaxAllowedDecimals = coinMaxAllowedDecimals,
            rate = rate,
        ),
        signState = signState,
        selectedFormat = selectedFormat,
    )

    OfflineSignEffect(
        signState = signState,
        currentSnackbar = currentSnackbar,
        onSnackbarChange = { currentSnackbar = it },
        callbacks = callbacks,
    )
    OfflineSignLayout(
        contentState = contentState,
        onFormatSelect = { selectedFormat = it },
        onInfoClick = { infoFormat = it },
        callbacks = callbacks,
        windowInsets = windowInsets,
    )
    infoFormat?.let { format ->
        val context = LocalContext.current
        val uriHandler = remember(context) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    LinkHelper.openLinkInAppBrowser(context, uri)
                }
            }
        }
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            InfoBottomSheet(
                title = stringResource(format.infoTitleRes),
                text = annotatedHtmlStringResource(
                    id = format.infoDescriptionRes,
                    linkStyle = SpanStyle(
                        color = ComposeAppTheme.colors.jacob,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                onDismiss = { infoFormat = null },
            )
        }
    }
}

internal data class OfflineBitcoinSignCallbacks(
    val onBackClick: () -> Unit,
    val onCancelClick: () -> Unit,
    val onSignClick: (OfflineTransactionFormat) -> Unit,
    val onSignStateConsumed: () -> Unit,
    val onSigned: (OfflineTransactionFormat) -> Unit,
)

private data class OfflineBitcoinSignContentState(
    val summaryState: OfflineBitcoinSignSummaryState,
    val signState: OfflineSignState,
    val selectedFormat: OfflineTransactionFormat,
)

private data class OfflineBitcoinSignSummaryState(
    val blockchainName: String,
    val amount: String,
    val amountFiat: String,
    val recipient: String,
    val fee: String,
)

@Composable
private fun OfflineSignEffect(
    signState: OfflineSignState,
    currentSnackbar: CustomSnackbar?,
    onSnackbarChange: (CustomSnackbar?) -> Unit,
    callbacks: OfflineBitcoinSignCallbacks,
) {
    val view = LocalView.current
    val currentOnSnackbarChange by rememberUpdatedState(onSnackbarChange)
    val currentCallbacks by rememberUpdatedState(callbacks)

    LaunchedEffect(signState) {
        when (signState) {
            OfflineSignState.Idle -> {
                currentSnackbar?.dismiss()
                currentOnSnackbarChange(null)
            }

            OfflineSignState.Signing -> {
                currentOnSnackbarChange(
                    HudHelper.showInProcessMessage(
                        view,
                        R.string.offline_transaction_signing,
                        SnackbarDuration.INDEFINITE,
                    )
                )
            }

            is OfflineSignState.Failed -> {
                currentSnackbar?.dismiss()
                currentOnSnackbarChange(null)
                HudHelper.showErrorMessage(
                    view,
                    signState.caution.description?.toString() ?: signState.caution.s.toString(),
                )
                currentCallbacks.onSignStateConsumed()
            }

            is OfflineSignState.Signed -> {
                currentSnackbar?.dismiss()
                currentOnSnackbarChange(null)
                currentCallbacks.onSigned(signState.format)
                currentCallbacks.onSignStateConsumed()
            }
        }
    }
}

@Composable
private fun OfflineSignLayout(
    contentState: OfflineBitcoinSignContentState,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
    callbacks: OfflineBitcoinSignCallbacks,
    windowInsets: WindowInsets,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.offline_transaction_sign_title),
                navigationIcon = {
                    HsBackButton(onClick = callbacks.onBackClick)
                },
                menuItems = listOf(),
            )
        },
        bottomBar = {
            OfflineSignBottomActions(
                signState = contentState.signState,
                selectedFormat = contentState.selectedFormat,
                callbacks = callbacks,
                windowInsets = windowInsets,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            OfflineSignScrollableContent(
                contentState = contentState,
                onFormatSelect = onFormatSelect,
                onInfoClick = onInfoClick,
            )
        }
    }
}

@Composable
private fun OfflineSignScrollableContent(
    contentState: OfflineBitcoinSignContentState,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(8.dp)
        OfflineSummarySection(
            summaryState = contentState.summaryState,
        )
        VSpacer(24.dp)
        OfflineFormatSection(
            selectedFormat = contentState.selectedFormat,
            onFormatSelect = onFormatSelect,
            onInfoClick = onInfoClick,
        )
    }
}

@Composable
private fun OfflineSummarySection(
    summaryState: OfflineBitcoinSignSummaryState,
) {
    SectionCaption(R.string.offline_transaction_what_you_sign)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_network),
            value = summaryState.blockchainName,
            borderTop = false,
        )
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_amount),
            value = summaryState.amount,
            secondaryValue = summaryState.amountFiat,
        )
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_recipient),
            value = summaryState.recipient,
            valueMaxLines = 2,
        )
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_fee),
            value = summaryState.fee,
        )
    }
}

@Composable
private fun OfflineFormatSection(
    selectedFormat: OfflineTransactionFormat,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
) {
    SectionCaption(R.string.offline_transaction_format)
    CellUniversalLawrenceSection(
        OfflineTransactionFormat.entries.map { format ->
            {
                OfflineFormatCell(
                    format = format,
                    selected = selectedFormat == format,
                    onClick = { onFormatSelect(format) },
                    onInfoClick = { onInfoClick(format) },
                )
            }
        }
    )
}

@Composable
private fun OfflineSignBottomActions(
    signState: OfflineSignState,
    selectedFormat: OfflineTransactionFormat,
    callbacks: OfflineBitcoinSignCallbacks,
    windowInsets: WindowInsets,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            title = if (signState is OfflineSignState.Signing) {
                stringResource(R.string.offline_transaction_signing)
            } else {
                stringResource(R.string.offline_transaction_button_sign)
            },
            enabled = signState !is OfflineSignState.Signing,
            onClick = { callbacks.onSignClick(selectedFormat) },
        )
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Button_Cancel),
            enabled = signState !is OfflineSignState.Signing,
            onClick = callbacks.onCancelClick,
        )
    }
}

@Composable
private fun OfflineFormatCell(
    format: OfflineTransactionFormat,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        HsCheckbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        subhead2_leah(
            modifier = Modifier
                .padding(start = 16.dp),
            text = stringResource(format.titleRes),
        )
        HsIconButton(
            onClick = onInfoClick,
            minWidth = 36.dp
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_info_20),
                contentDescription = stringResource(R.string.Info_Title),
                tint = ComposeAppTheme.colors.grey,
            )
        }
    }
}

private val OfflineTransactionFormat.titleRes: Int
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> R.string.offline_transaction_pcash_format
        OfflineTransactionFormat.Raw -> R.string.offline_transaction_raw_format
    }

private val OfflineTransactionFormat.infoTitleRes: Int
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> R.string.offline_transaction_pcash_info_title
        OfflineTransactionFormat.Raw -> R.string.offline_transaction_raw_info_title
    }

private val OfflineTransactionFormat.infoDescriptionRes: Int
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> R.string.offline_transaction_pcash_info_description
        OfflineTransactionFormat.Raw -> R.string.offline_transaction_raw_info_description
    }

private fun SendConfirmationData.toOfflineSignSummaryState(
    blockchainName: String,
    coinMaxAllowedDecimals: Int,
    rate: CurrencyValue?,
): OfflineBitcoinSignSummaryState =
    OfflineBitcoinSignSummaryState(
        blockchainName = blockchainName,
        amount = App.numberFormatter.formatCoinFull(
            amount,
            coin.code,
            coinMaxAllowedDecimals,
        ),
        amountFiat = rate?.copy(value = amount.times(rate.value))?.getFormattedFull().orEmpty(),
        recipient = address.hex,
        fee = fee?.let {
            App.numberFormatter.formatCoinFull(
                it,
                feeCoin.code,
                coinMaxAllowedDecimals,
            )
        } ?: "---",
    )

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBitcoinSignScreenPreview() {
    ComposeAppTheme {
        OfflineSignLayout(
            contentState = OfflineBitcoinSignContentState(
                summaryState = OfflineBitcoinSignSummaryState(
                    blockchainName = "Bitcoin",
                    amount = "0.001516 BTC",
                    amountFiat = "$ 1.99",
                    recipient = "bc1qz7xsrl9mw45lj3hvl2t7arcjrs0umyyf69c4en",
                    fee = "0.00000141 BTC",
                ),
                signState = OfflineSignState.Idle,
                selectedFormat = OfflineTransactionFormat.Pcash,
            ),
            onFormatSelect = {},
            onInfoClick = {},
            callbacks = previewOfflineBitcoinSignCallbacks,
            windowInsets = NavigationBarDefaults.windowInsets,
        )
    }
}

private val previewOfflineBitcoinSignCallbacks = OfflineBitcoinSignCallbacks(
    onBackClick = {},
    onCancelClick = {},
    onSignClick = {},
    onSignStateConsumed = {},
    onSigned = {},
)

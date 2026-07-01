package cash.p.terminal.modules.send.offline

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.getShape
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl

@Composable
internal fun OfflineBroadcastScreen(
    uiState: OfflineBroadcastUiState,
    onPickNetwork: () -> Unit,
    onSelectBlockchain: (Blockchain) -> Unit,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(titleResId(uiState.step)),
                navigationIcon = { HsBackButton(onClick = onBack) },
                menuItems = emptyList(),
            )
        },
        bottomBar = {
            OfflineBroadcastBottomBar(
                uiState = uiState,
                onPrimaryAction = onPrimaryAction,
                onRetry = onRetry,
                onClose = onClose,
                windowInsets = windowInsets,
            )
        },
    ) { paddingValues ->
        when (uiState.step) {
            // Transient: prefillAndAdvance moves off this step on first composition.
            OfflineBroadcastStep.Loading -> Unit

            OfflineBroadcastStep.SelectBlockchain -> BlockchainPicker(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                blockchains = uiState.selectableBlockchains,
                selected = uiState.selectedBlockchain,
                onSelectBlockchain = onSelectBlockchain,
            )

            OfflineBroadcastStep.Confirm,
            OfflineBroadcastStep.Result -> Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (uiState.step) {
                    OfflineBroadcastStep.Confirm -> uiState.confirm?.let {
                        ConfirmContent(confirm = it, onPickNetwork = onPickNetwork)
                    }

                    OfflineBroadcastStep.Result -> uiState.result?.let { ResultContent(result = it) }
                }
            }
        }
    }
}

private fun titleResId(step: OfflineBroadcastStep): Int = when (step) {
    OfflineBroadcastStep.SelectBlockchain -> R.string.offline_broadcast_blockchain
    OfflineBroadcastStep.Result -> R.string.Button_Done
    else -> R.string.offline_broadcast_check_title
}

@Composable
private fun ConfirmContent(confirm: OfflineBroadcastConfirm, onPickNetwork: () -> Unit) {
    VSpacer(12.dp)
    SectionCaption(R.string.offline_broadcast_details)
    NetworkRow(confirm = confirm, onPickNetwork = onPickNetwork)
    VSpacer(12.dp)
    RawTransactionSection(rawHex = confirm.rawHex)
    InfoText(text = stringResource(R.string.offline_broadcast_disclaimer))
    confirm.enableNetworkName?.let { networkName ->
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.offline_broadcast_enable_network_warning, networkName),
        )
        VSpacer(12.dp)
    }
}

// Fixed network (pcash payload) renders as a plain label/value cell; a plain RAW HEX lets the user
// pick the broadcasting network, so it becomes a tappable row with a chevron.
@Composable
private fun NetworkRow(confirm: OfflineBroadcastConfirm, onPickNetwork: () -> Unit) {
    SectionUniversalLawrence {
        if (confirm.selectable) {
            CellUniversal(borderTop = false, onClick = onPickNetwork) {
                Icon(
                    painter = painterResource(R.drawable.ic_blocks_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                )
                HSpacer(16.dp)
                body_leah(text = stringResource(R.string.offline_broadcast_blockchain))
                Spacer(modifier = Modifier.weight(1f))
                confirm.blockchainName?.let { subhead2_leah(text = it) }
                    ?: subhead2_grey(text = stringResource(R.string.offline_broadcast_select_network))
                HSpacer(8.dp)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_big_down_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                )
            }
        } else {
            OfflineSummaryCell(
                title = stringResource(R.string.offline_transaction_network),
                value = confirm.blockchainName.orEmpty(),
                borderTop = false,
            )
        }
    }
}

@Composable
private fun BlockchainPicker(
    modifier: Modifier = Modifier,
    blockchains: List<Blockchain>,
    selected: Blockchain?,
    onSelectBlockchain: (Blockchain) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
    ) {
        itemsIndexed(
            items = blockchains,
            key = { _, blockchain -> blockchain.uid },
        ) { index, blockchain ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(getShape(blockchains.size, index))
                    .background(ComposeAppTheme.colors.lawrence),
            ) {
                SectionUniversalItem(borderTop = index != 0) {
                    BlockchainCell(
                        blockchain = blockchain,
                        selected = blockchain.type == selected?.type,
                        onClick = { onSelectBlockchain(blockchain) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockchainCell(
    blockchain: Blockchain,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { this.selected = selected },
        onClick = onClick,
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = blockchain.type.imageUrl,
                error = painterResource(R.drawable.ic_platform_placeholder_32),
            ),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        body_leah(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            text = blockchain.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.jacob,
            )
        }
    }
}

@Composable
private fun ResultContent(result: OfflineBroadcastResult) {
    VSpacer(12.dp)
    when (result) {
        is OfflineBroadcastResult.Success -> SuccessResult(result)
        is OfflineBroadcastResult.Error -> ErrorResult(result)
    }
}

@Composable
private fun SuccessResult(result: OfflineBroadcastResult.Success) {
    val titleRes = if (result.queued) {
        R.string.offline_signed_status_pending
    } else {
        R.string.offline_broadcast_success_title
    }
    val descriptionRes = if (result.queued) {
        R.string.send_success_queued
    } else {
        R.string.offline_broadcast_success_description
    }
    VSpacer(44.dp)
    OfflineStatusBlock(
        icon = if (result.queued) R.drawable.ic_info_20 else R.drawable.ic_checkmark_20,
        iconTint = if (result.queued) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.remus,
        style = if (result.queued) OfflineStatusBlockStyle.Neutral else OfflineStatusBlockStyle.Success,
        title = stringResource(titleRes),
    ) {
        VSpacer(8.dp)
        ResultDescription(stringResource(descriptionRes))
    }
    VSpacer(24.dp)
    SectionCaption(R.string.offline_broadcast_details)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_network),
            value = result.networkName,
            borderTop = false,
        )
        TransactionIdCell(txHash = result.txHash)
    }
    result.explorerUrl?.takeIf { !result.queued }?.let {
        VSpacer(12.dp)
        SectionUniversalLawrence {
            ExplorerCell(url = it)
        }
    }
    VSpacer(24.dp)
}

@Composable
private fun ErrorResult(result: OfflineBroadcastResult.Error) {
    VSpacer(44.dp)
    OfflineStatusBlock(
        icon = R.drawable.ic_close_24,
        iconTint = ComposeAppTheme.colors.lucian,
        style = OfflineStatusBlockStyle.Error,
        title = stringResource(R.string.offline_broadcast_error_title),
    ) {
        VSpacer(8.dp)
        ResultDescription(result.message)
    }
    VSpacer(24.dp)
    SectionCaption(R.string.offline_broadcast_details)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(R.string.offline_transaction_network),
            value = result.networkName,
            borderTop = false,
        )
    }
    VSpacer(12.dp)
    RawTransactionSection(rawHex = result.rawHex)
    VSpacer(24.dp)
}

@Composable
private fun ResultDescription(text: String) {
    subhead2_grey(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = text,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TransactionIdCell(txHash: String) {
    val view = LocalView.current
    val copy: () -> Unit = {
        TextHelper.copyText(txHash)
        HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
    }
    CellUniversal(borderTop = true) {
        subhead2_grey(
            text = stringResource(R.string.offline_broadcast_tx_id),
            modifier = Modifier.padding(end = 16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonSecondaryDefault(
                title = txHash.shorten(),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(28.dp),
                overflow = TextOverflow.MiddleEllipsis,
                onClick = copy,
            )
            HSpacer(8.dp)
            ButtonSecondaryCircle(
                icon = R.drawable.ic_copy_20,
                onClick = copy,
            )
        }
    }
}

@Composable
private fun ExplorerCell(url: String) {
    val context = LocalContext.current
    CellUniversal(
        borderTop = false,
        onClick = { LinkHelper.openLinkInAppBrowser(context, url) },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_globe_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey,
        )
        HSpacer(16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.offline_broadcast_view_in_explorer),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_big_forward_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey,
        )
    }
}

@Composable
private fun OfflineBroadcastBottomBar(
    uiState: OfflineBroadcastUiState,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets,
) {
    val hasActions = uiState.step == OfflineBroadcastStep.Confirm ||
        uiState.step == OfflineBroadcastStep.Result
    if (!hasActions) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        when (uiState.step) {
            OfflineBroadcastStep.Confirm -> ConfirmActions(uiState, onPrimaryAction, onClose)
            OfflineBroadcastStep.Result -> ResultActions(uiState.result, onRetry, onClose)
        }
    }
}

@Composable
private fun ColumnScope.ConfirmActions(
    uiState: OfflineBroadcastUiState,
    onPrimaryAction: () -> Unit,
    onClose: () -> Unit,
) {
    val confirm = uiState.confirm
    val broadcasting = uiState.broadcasting
    val preparing = confirm?.action is OfflineBroadcastConfirmAction.PreparingNetwork
    PrimaryConfirmButton(confirm, broadcasting, onPrimaryAction)
    VSpacer(12.dp)
    ButtonPrimaryTransparent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.Button_Reject),
        enabled = !broadcasting && !preparing,
        onClick = onClose,
    )
}

// The primary button mirrors the confirm action: enable a missing network, show preparing while the
// wallet/adapter start up, or send once everything is ready. The ViewModel decides what the single
// action does, so the UI forwards every tap to onPrimaryAction.
@Composable
private fun PrimaryConfirmButton(
    confirm: OfflineBroadcastConfirm?,
    broadcasting: Boolean,
    onPrimaryAction: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    when (val action = confirm?.action) {
        is OfflineBroadcastConfirmAction.EnableNetwork -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(R.string.offline_broadcast_enable_network_button, action.blockchainName),
            onClick = onPrimaryAction,
        )

        is OfflineBroadcastConfirmAction.PreparingNetwork -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(R.string.offline_broadcast_preparing_network_button, action.blockchainName),
            enabled = false,
            loadingIndicator = true,
            onClick = {},
        )

        else -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(
                if (broadcasting) R.string.offline_broadcast_sending
                else R.string.offline_broadcast_send_button
            ),
            enabled = confirm?.canBroadcast == true && !broadcasting,
            loadingIndicator = broadcasting,
            onClick = onPrimaryAction,
        )
    }
}

@Composable
private fun ColumnScope.ResultActions(
    result: OfflineBroadcastResult?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    when (result) {
        is OfflineBroadcastResult.Success -> ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Button_Done),
            onClick = onClose,
        )

        is OfflineBroadcastResult.Error -> {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.try_again),
                onClick = onRetry,
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Cancel),
                onClick = onClose,
            )
        }

        null -> Unit
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastConfirmPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = true,
                    blockchainName = "Binance Smart Chain",
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastEnableNetworkPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = false,
                    blockchainName = "Bitcoin",
                    action = OfflineBroadcastConfirmAction.EnableNetwork("Bitcoin"),
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastPreparingNetworkPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = false,
                    blockchainName = "Bitcoin",
                    action = OfflineBroadcastConfirmAction.PreparingNetwork("Bitcoin"),
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastSuccessErrorPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Error(
                    networkName = "Binance Smart Chain",
                    rawHex = "0200000000010112ab34cd56ef7890",
                    message = "Failed to broadcast transaction. Please try again.",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastSuccessPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Success(
                    networkName = "Binance Smart Chain",
                    txHash = "0MXgWabcdefghijklmnopL7KCS",
                    queued = false,
                    explorerUrl = "https://bscscan.com/tx/0x",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastQueuedPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Success(
                    networkName = "Bitcoin",
                    txHash = "7f8a9b0c1d2e3f405162738495a6b7c8d9e0f11223344556677889900aabbccd",
                    queued = true,
                    explorerUrl = "https://blockchair.com/bitcoin/transaction/",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

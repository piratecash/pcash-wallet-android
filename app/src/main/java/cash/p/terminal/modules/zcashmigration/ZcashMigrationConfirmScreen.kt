package cash.p.terminal.modules.zcashmigration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.fee.FeeCell
import cash.p.terminal.modules.fee.FeeItem
import cash.p.terminal.modules.send.ConfirmAmountCell
import cash.p.terminal.modules.send.SendButton
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.ui.compose.components.SectionTitleCell
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.Coin
import kotlinx.coroutines.delay

private const val SUCCESS_CLOSE_DELAY_MILLIS = 1200L

@Composable
internal fun ZcashMigrationConfirmScreen(
    uiState: ZcashMigrationUiState,
    coin: Coin,
    onMigrateClick: () -> Unit,
    onClose: () -> Unit,
) {
    SendResultHandler(uiState.sendResult, onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.tyler)
    ) {
        AppBar(
            title = stringResource(R.string.zcash_migration_confirm_title),
            navigationIcon = { HsBackButton(onClick = onClose) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            MigrationDetails(
                uiState = uiState,
                coin = coin,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 106.dp)
            )
            SendButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                sendResult = uiState.sendResult,
                onClickSend = onMigrateClick,
                enabled = uiState.migrateEnabled,
                title = stringResource(R.string.balance_zcash_migration_migrate)
            )
        }
    }
}

@Composable
private fun MigrationDetails(
    uiState: ZcashMigrationUiState,
    coin: Coin,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        VSpacer(12.dp)
        CellUniversalLawrenceSection(
            listOf({
                SectionTitleCell(
                    title = stringResource(R.string.transactions_migrate),
                    value = stringResource(R.string.transactions_migrate_to_ironwood),
                    iconResId = R.drawable.ic_migrate_24
                )
            }, {
                ConfirmAmountCell(uiState.amountFiat, uiState.amount, coin)
            })
        )
        VSpacer(16.dp)
        CellUniversalLawrenceSection {
            FeeCell(
                title = stringResource(R.string.Send_Fee),
                info = stringResource(R.string.Send_Fee_Info),
                value = uiState.fee?.let { FeeItem(it, uiState.feeFiat) },
                viewState = if (uiState.fee == null && uiState.error == null) {
                    ViewState.Loading
                } else {
                    null
                }
            )
        }
        VSpacer(16.dp)
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(R.string.zcash_migration_publicly_visible_title),
            text = stringResource(R.string.zcash_migration_publicly_visible_description),
            icon = R.drawable.ic_attention_20
        )
        // The send failure is shown here as well: its snackbar disappears and would otherwise
        // leave a disabled button with no explanation.
        val caution = uiState.error ?: (uiState.sendResult as? SendResult.Failed)?.caution
        caution?.let {
            VSpacer(16.dp)
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = it.displayText,
                icon = R.drawable.ic_attention_20
            )
        }
    }
}

@Composable
private fun SendResultHandler(
    sendResult: SendResult?,
    onSuccess: () -> Unit,
) {
    val view = LocalView.current
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    // Caution strings are @Composable, so they must be resolved outside the LaunchedEffect.
    val failedMessage = (sendResult as? SendResult.Failed)?.caution?.displayText

    LaunchedEffect(sendResult) {
        when (sendResult) {
            is SendResult.Sent -> {
                HudHelper.showSuccessMessage(view, R.string.Send_Success, SnackbarDuration.LONG)
                delay(SUCCESS_CLOSE_DELAY_MILLIS)
                currentOnSuccess()
            }

            is SendResult.Failed -> HudHelper.showErrorMessage(view, failedMessage.orEmpty())

            else -> Unit
        }
    }
}

private val HSCaution.displayText: String
    @Composable get() = getDescription() ?: getString()

@Preview
@Composable
private fun ZcashMigrationConfirmScreenPreview() {
    ComposeAppTheme {
        ZcashMigrationConfirmScreen(
            uiState = ZcashMigrationUiState(
                amount = "1.2345 ZEC",
                amountFiat = "$123.45",
                fee = "0.0002 ZEC",
                feeFiat = "$0.02",
                migrateEnabled = true,
                sendResult = null,
                error = null,
            ),
            coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
            onMigrateClick = {},
            onClose = {},
        )
    }
}

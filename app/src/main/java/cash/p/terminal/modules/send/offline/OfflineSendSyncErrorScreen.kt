package cash.p.terminal.modules.send.offline

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal data class OfflineSendSyncErrorState(
    val title: String,
    val coinCode: String,
    val noConnection: Boolean,
    val inProgress: Boolean,
    val sourceChangeable: Boolean,
)

internal data class OfflineSendSyncErrorCallbacks(
    val onBackClick: () -> Unit,
    val onRetryClick: () -> Unit,
    val onChangeSourceClick: () -> Unit,
    val onSignOfflineClick: () -> Unit,
)

@Composable
internal fun OfflineSendSyncErrorScreen(
    state: OfflineSendSyncErrorState,
    callbacks: OfflineSendSyncErrorCallbacks,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = state.title,
                navigationIcon = {
                    HsBackButton(onClick = callbacks.onBackClick)
                },
                menuItems = emptyList(),
            )
        },
        bottomBar = {
            OfflineSyncActions(
                noConnection = state.noConnection,
                inProgress = state.inProgress,
                sourceChangeable = state.sourceChangeable,
                onRetryClick = callbacks.onRetryClick,
                onChangeSourceClick = callbacks.onChangeSourceClick,
                onSignOfflineClick = callbacks.onSignOfflineClick,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            OfflineSyncErrorContent(state)
        }
    }
}

@Composable
private fun OfflineSyncErrorContent(state: OfflineSendSyncErrorState) {
    when {
        state.inProgress -> OfflineSyncProgressBlock()
        state.noConnection -> OfflineStatusBlock(
            icon = R.drawable.ic_not_available,
            iconTint = ComposeAppTheme.colors.grey,
            style = OfflineStatusBlockStyle.Neutral,
            title = stringResource(R.string.Hud_Text_NoInternet),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.offline_send_no_connection_description),
                style = ComposeAppTheme.typography.subhead1,
                color = ComposeAppTheme.colors.grey,
                textAlign = TextAlign.Center,
            )
        }
        else -> OfflineStatusBlock(
            icon = R.drawable.ic_attention_red_24,
            iconTint = ComposeAppTheme.colors.lucian,
            style = OfflineStatusBlockStyle.Error,
            title = "${stringResource(R.string.BalanceSyncError_Title)} - ${state.coinCode}",
        ) {
            OfflineSyncErrorDescription(
                description = stringResource(R.string.offline_send_sync_error_description)
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "No Connection", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSendSyncErrorScreenNoConnectionPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = true,
                inProgress = false,
                sourceChangeable = false,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Sync Error", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSendSyncErrorScreenProviderErrorPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = false,
                inProgress = false,
                sourceChangeable = true,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Progress", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineSendSyncErrorScreenProgressPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = true,
                inProgress = true,
                sourceChangeable = false,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

private val previewOfflineSendSyncErrorCallbacks = OfflineSendSyncErrorCallbacks(
    onBackClick = {},
    onRetryClick = {},
    onChangeSourceClick = {},
    onSignOfflineClick = {},
)

@Composable
private fun OfflineSyncProgressBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.lawrence),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = ComposeAppTheme.colors.jacob,
                strokeWidth = 4.dp,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.Balance_Syncing),
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.leah,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OfflineSyncErrorDescription(description: String) {
    val parts = remember(description) {
        description.split("\n\n", limit = SYNC_ERROR_DESCRIPTION_PARTS)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        parts.getOrNull(SYNC_ERROR_INTRO_INDEX)?.takeIf { it.isNotBlank() }?.let { intro ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = intro,
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.grey,
                textAlign = TextAlign.Center,
            )
        }

        parts.getOrNull(SYNC_ERROR_ACTIONS_INDEX)?.takeIf { it.isNotBlank() }?.let { actions ->
            Spacer(Modifier.height(4.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = actions,
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.leah,
                textAlign = TextAlign.Start,
            )
        }

        parts.getOrNull(SYNC_ERROR_FOOTER_INDEX)?.takeIf { it.isNotBlank() }?.let { footer ->
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = footer,
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.grey,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun OfflineSyncActions(
    noConnection: Boolean,
    inProgress: Boolean,
    sourceChangeable: Boolean,
    onRetryClick: () -> Unit,
    onChangeSourceClick: () -> Unit,
    onSignOfflineClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.try_again),
            enabled = !inProgress,
            onClick = onRetryClick,
        )
        if (!noConnection && sourceChangeable) {
            Spacer(Modifier.height(12.dp))
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.BalanceSyncError_ButtonChangeSource),
                enabled = !inProgress,
                onClick = onChangeSourceClick,
            )
        }
        Spacer(Modifier.height(12.dp))
        ButtonPrimaryTransparent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.offline_transaction_sign_offline),
            onClick = onSignOfflineClick,
        )
    }
}

private const val SYNC_ERROR_DESCRIPTION_PARTS = 3
private const val SYNC_ERROR_INTRO_INDEX = 0
private const val SYNC_ERROR_ACTIONS_INDEX = 1
private const val SYNC_ERROR_FOOTER_INDEX = 2

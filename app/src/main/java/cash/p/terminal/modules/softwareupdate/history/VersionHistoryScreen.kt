package cash.p.terminal.modules.softwareupdate.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun VersionHistoryScreen(
    uiState: VersionHistoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVersionClick: (String, Boolean) -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.version_history_title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val isEmpty = uiState.current == null && uiState.oldMinors.isEmpty()
        LazyColumn(
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            uiState.current?.let { current ->
                item {
                    CellUniversalLawrenceSection {
                        CurrentVersionRow(current) {
                            onVersionClick(current.minor, uiState.currentIsActiveBranch)
                        }
                    }
                    VSpacer(24.dp)
                }
            }
            if (uiState.oldMinors.isNotEmpty()) {
                item {
                    caption_grey(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        text = stringResource(R.string.update_old_versions_section).uppercase(),
                    )
                }
                item {
                    val cells: List<@Composable () -> Unit> = uiState.oldMinors.map { minor ->
                        { OldVersionRow(minor) { onVersionClick(minor, false) } }
                    }
                    CellUniversalLawrenceSection(composableItems = cells)
                }
            }
            if (uiState.loading && isEmpty) {
                item { LoadingBlock() }
            }
            if (uiState.error && isEmpty) {
                item { HistoryErrorState(onRetry = onRetry) }
            }
        }
    }
}

@Composable
private fun HistoryErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        subhead2_grey(text = stringResource(R.string.Error))
        VSpacer(12.dp)
        ButtonPrimaryYellow(
            title = stringResource(R.string.Button_Retry),
            onClick = onRetry,
        )
    }
}

@Composable
private fun CurrentVersionRow(release: AppRelease, onClick: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            subhead2_jacob(text = stringResource(R.string.update_current_version_section))
            VSpacer(4.dp)
            body_leah(text = stringResource(R.string.update_version_label, release.version))
            VSpacer(2.dp)
            subhead2_grey(
                text = DateUtils.getRelativeTimeSpanString(release.publishedAt.toEpochMilli()).toString(),
            )
        }
        ArrowIcon()
    }
}

@Composable
private fun OldVersionRow(minor: String, onClick: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.update_version_label, "$minor.x"),
        )
        ArrowIcon()
    }
}

@Composable
private fun ArrowIcon() {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(R.drawable.ic_arrow_right),
        contentDescription = null,
        tint = ComposeAppTheme.colors.grey,
    )
}

@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = ComposeAppTheme.colors.grey,
        )
    }
}

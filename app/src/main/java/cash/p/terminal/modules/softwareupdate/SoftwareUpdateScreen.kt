package cash.p.terminal.modules.softwareupdate

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cash.p.terminal.R
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.modules.softwareupdate.domain.ChangelogSnippet
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.UpdateCheckInterval
import cash.p.terminal.modules.softwareupdate.domain.UpdateStatus
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.ui.compose.components.SelectorDialogCompose
import cash.p.terminal.ui.compose.components.SelectorItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.micro_grey50
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
internal fun SoftwareUpdateScreen(
    uiState: SoftwareUpdateUiState,
    onBack: () -> Unit,
    onIntervalChange: (UpdateCheckInterval) -> Unit,
    onRetry: () -> Unit,
    onHistoryClick: () -> Unit,
    onDetailsClick: (String) -> Unit,
    onUpdateNowClick: (AppRelease) -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.update_screen_title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf {
                    HsSettingCell(
                        title = R.string.version_history_title,
                        value = uiState.currentVersion,
                        onClick = onHistoryClick,
                    )
                },
            )
            VSpacer(12.dp)
            IntervalCell(uiState.interval, onIntervalChange)
            uiState.lastCheckTimestamp?.let { timestamp ->
                subhead2_grey(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    text = stringResource(R.string.update_last_check_date, formatDate(timestamp)),
                )
            }

            VSpacer(32.dp)
            when (val status = uiState.updateStatus) {
                UpdateStatus.Unknown -> CheckingBlock()
                UpdateStatus.UpToDate -> UpToDateBlock(
                    version = uiState.currentVersion,
                    onDetailsClick = { onDetailsClick(uiState.currentVersion.toMinor()) },
                )

                is UpdateStatus.Available -> AvailableSection(
                    status = status,
                    installSource = uiState.installSource,
                    onDetailsClick = { onDetailsClick(status.release.minor) },
                    onUpdateNowClick = { onUpdateNowClick(status.release) },
                )

                UpdateStatus.Error -> ErrorBlock(onRetry)
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun IntervalCell(
    interval: UpdateCheckInterval,
    onIntervalChange: (UpdateCheckInterval) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        SelectorDialogCompose(
            title = stringResource(R.string.auto_check_updates),
            items = UpdateCheckInterval.entries.map { option ->
                SelectorItem(
                    title = stringResource(option.fullLabelRes()),
                    selected = option == interval,
                    item = option,
                )
            },
            onDismissRequest = { showDialog = false },
            onSelectItem = { selected ->
                onIntervalChange(selected)
                showDialog = false
            },
        )
    }

    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { showDialog = true },
            ) {
                body_leah(text = stringResource(R.string.auto_check_updates), modifier = Modifier.weight(1f))
                subhead1_grey(text = stringResource(interval.labelRes()))
                Icon(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                    painter = painterResource(R.drawable.ic_down_24),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                )
            }
        },
    )
}

@Composable
private fun CheckingBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = ComposeAppTheme.colors.grey,
        )
        VSpacer(16.dp)
        subhead2_grey(text = stringResource(R.string.update_checking))
    }
}

@Composable
private fun UpToDateBlock(version: String, onDetailsClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.steel10),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(72.dp),
                painter = painterResource(R.drawable.ic_checkmark_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey,
            )
        }
        VSpacer(24.dp)
        headline2_leah(text = stringResource(R.string.update_up_to_date))
        VSpacer(4.dp)
        subhead2_grey(text = stringResource(R.string.update_version_label, version))
        VSpacer(12.dp)
        subhead2_jacob(
            modifier = Modifier
                .clickable(onClick = onDetailsClick)
                .padding(8.dp),
            text = stringResource(R.string.update_details),
        )
    }
}

@Composable
private fun AvailableSection(
    status: UpdateStatus.Available,
    installSource: InstallSource,
    onDetailsClick: () -> Unit,
    onUpdateNowClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            text = stringResource(R.string.update_available_title),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ComposeAppTheme.colors.steel20, RoundedCornerShape(12.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AvailableCardHeader(status.release)
                VSpacer(20.dp)
                val snippet = status.changelogSnippet
                subhead2_grey(
                    text = if (snippet != null) {
                        stringResource(R.string.update_improvements_fixes, snippet.improvements, snippet.fixes)
                    } else {
                        stringResource(R.string.update_available_title)
                    },
                )
                VSpacer(4.dp)
                subhead2_jacob(
                    modifier = Modifier
                        .clickable(onClick = onDetailsClick)
                        .padding(vertical = 8.dp),
                    text = stringResource(R.string.update_details_ellipsis),
                )
            }
            HorizontalDivider(thickness = 1.dp, color = ComposeAppTheme.colors.steel10)
            Column(modifier = Modifier.padding(16.dp)) {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.update_now),
                    onClick = onUpdateNowClick,
                )
                VSpacer(8.dp)
                micro_grey50(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.update_source, stringResource(installSource.labelRes())),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AvailableCardHeader(release: AppRelease) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val appIcon = remember(inspection) {
        if (inspection) {
            null
        } else {
            tryOrNull {
                context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap().asImageBitmap()
            }
        }
    }
    val iconModifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (appIcon != null) {
            Image(modifier = iconModifier, bitmap = appIcon, contentDescription = null)
        } else {
            Image(
                modifier = iconModifier,
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            body_leah(text = stringResource(R.string.update_version_label, release.version))
            release.apkSizeBytes?.let { size ->
                subhead2_grey(text = Formatter.formatShortFileSize(context, size))
            }
        }
    }
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        subhead2_grey(text = stringResource(R.string.Error))
        VSpacer(12.dp)
        ButtonPrimaryYellow(
            title = stringResource(R.string.Button_Retry),
            onClick = onRetry,
        )
    }
}

private fun UpdateCheckInterval.labelRes(): Int = when (this) {
    UpdateCheckInterval.DAY -> R.string.update_interval_day
    UpdateCheckInterval.WEEK -> R.string.update_interval_week
    UpdateCheckInterval.MONTH -> R.string.update_interval_month
}

private fun UpdateCheckInterval.fullLabelRes(): Int = when (this) {
    UpdateCheckInterval.DAY -> R.string.update_interval_day_full
    UpdateCheckInterval.WEEK -> R.string.update_interval_week_full
    UpdateCheckInterval.MONTH -> R.string.update_interval_month_full
}

private fun InstallSource.labelRes(): Int = when (this) {
    InstallSource.GOOGLE_PLAY -> R.string.update_source_google_play
    InstallSource.FDROID -> R.string.update_source_fdroid
    InstallSource.OTHER -> R.string.update_source_github
}

private fun String.toMinor(): String = split('.').take(2).joinToString(".")

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG).format(Date(timestamp))

@Preview
@Composable
private fun SoftwareUpdateScreenPreview() {
    ComposeAppTheme {
        SoftwareUpdateScreen(
            uiState = SoftwareUpdateUiState(
                currentVersion = "0.58.0",
                interval = UpdateCheckInterval.DAY,
                lastCheckTimestamp = 0L,
                updateStatus = UpdateStatus.UpToDate,
                installSource = InstallSource.GOOGLE_PLAY,
            ),
            onBack = {},
            onIntervalChange = {},
            onRetry = {},
            onHistoryClick = {},
            onDetailsClick = {},
            onUpdateNowClick = {},
        )
    }
}

@Preview
@Composable
private fun SoftwareUpdateScreenUpdatePreview() {
    ComposeAppTheme {
        SoftwareUpdateScreen(
            uiState = SoftwareUpdateUiState(
                currentVersion = "0.58.0",
                interval = UpdateCheckInterval.DAY,
                lastCheckTimestamp = 0L,
                updateStatus = UpdateStatus.Available(
                    release = AppRelease(
                        version = "0.58.0",
                        minor = "0.58",
                        tagName = "v0.58.0-fdroid",
                        publishedAt = Instant.EPOCH,
                        htmlUrl = "https://github.com/piratecash/pcash-wallet-android/releases",
                        apkSizeBytes = 12345678L,
                        apkDownloadUrl = "https://example.apk",
                    ),
                    changelogSnippet = ChangelogSnippet(improvements = 5, fixes = 3)
                ),
                installSource = InstallSource.GOOGLE_PLAY,
            ),
            onBack = {},
            onIntervalChange = {},
            onRetry = {},
            onHistoryClick = {},
            onDetailsClick = {},
            onUpdateNowClick = {},
        )
    }
}

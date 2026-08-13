package cash.p.terminal.ui.compose.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Epoch millis of the first block of [blockchainType], used as the lower bound
 * for the restore-date picker. Only blockchains that support restoring by date are handled.
 */
fun restoreGenesisDateMillis(blockchainType: BlockchainType): Long {
    val genesisDate = when (blockchainType) {
        BlockchainType.Monero -> LocalDate.of(2014, 4, 18)
        BlockchainType.Zcash -> LocalDate.of(2016, 10, 28)
        else -> error("No restore genesis date defined for $blockchainType")
    }
    return genesisDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** Epoch millis of now, used as the upper bound for the restore-date picker. */
fun restoreMaxDateMillis(): Long = System.currentTimeMillis()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDateBottomSheet(
    initialDateMillis: Long?,
    minDateMillis: Long,
    maxDateMillis: Long,
    onDateSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideAndDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val minYear = remember(minDateMillis) { epochMillisToYear(minDateMillis) }
    val maxYear = remember(maxDateMillis) { epochMillisToYear(maxDateMillis) }
    val selectableDates = remember(minDateMillis, maxDateMillis, minYear, maxYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in minDateMillis..maxDateMillis

            override fun isSelectableYear(year: Int): Boolean =
                year in minYear..maxYear
        }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: maxDateMillis,
        yearRange = minYear..maxYear,
        selectableDates = selectableDates,
    )

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            title = stringResource(R.string.select_date_title),
            onCloseClick = hideAndDismiss
        ) {
            subhead2_grey(
                text = stringResource(R.string.select_date_description),
                modifier = Modifier.padding(
                    start = 32.dp,
                    end = 32.dp,
                    bottom = 12.dp
                )
            )
            DatePicker(
                modifier = Modifier.fillMaxWidth(),
                state = datePickerState,
                title = null,
                showModeToggle = false,
                colors = selectDateBottomSheetColors(),
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_Apply),
                enabled = datePickerState.selectedDateMillis != null,
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelect(millis.toUtcLocalDate())
                    }
                    hideAndDismiss()
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun selectDateBottomSheetColors() = DatePickerDefaults.colors(
    containerColor = ComposeAppTheme.colors.lawrence,
    titleContentColor = ComposeAppTheme.colors.grey,
    headlineContentColor = ComposeAppTheme.colors.leah,
    weekdayContentColor = ComposeAppTheme.colors.grey,
    subheadContentColor = ComposeAppTheme.colors.leah,
    navigationContentColor = ComposeAppTheme.colors.leah,
    yearContentColor = ComposeAppTheme.colors.leah,
    disabledYearContentColor = ComposeAppTheme.colors.grey50,
    currentYearContentColor = ComposeAppTheme.colors.jacob,
    selectedYearContentColor = ComposeAppTheme.colors.dark,
    disabledSelectedYearContentColor = ComposeAppTheme.colors.grey50,
    selectedYearContainerColor = ComposeAppTheme.colors.jacob,
    disabledSelectedYearContainerColor = ComposeAppTheme.colors.steel20,
    dayContentColor = ComposeAppTheme.colors.leah,
    disabledDayContentColor = ComposeAppTheme.colors.grey50,
    selectedDayContentColor = ComposeAppTheme.colors.dark,
    disabledSelectedDayContentColor = ComposeAppTheme.colors.grey50,
    selectedDayContainerColor = ComposeAppTheme.colors.jacob,
    disabledSelectedDayContainerColor = ComposeAppTheme.colors.steel20,
    todayContentColor = ComposeAppTheme.colors.jacob,
    todayDateBorderColor = ComposeAppTheme.colors.jacob,
    dayInSelectionRangeContentColor = ComposeAppTheme.colors.leah,
    dayInSelectionRangeContainerColor = ComposeAppTheme.colors.steel20,
    dividerColor = ComposeAppTheme.colors.steel20,
)

private fun epochMillisToYear(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).year

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun SelectDateBottomSheetPreview() {
    ComposeAppTheme {
        SelectDateBottomSheet(
            initialDateMillis = null,
            minDateMillis = 0L,
            maxDateMillis = System.currentTimeMillis(),
            onDateSelect = {},
            onDismiss = {}
        )
    }
}

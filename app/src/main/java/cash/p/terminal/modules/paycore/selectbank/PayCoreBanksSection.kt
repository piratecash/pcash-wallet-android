package cash.p.terminal.modules.paycore.selectbank

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.paycore.PayCoreBankResponse
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.getShape
import cash.p.terminal.ui_compose.components.showDivider
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal fun LazyListScope.payCoreBankSectionItems(
    banks: List<PayCoreBankResponse>,
    onBankClick: (PayCoreBankResponse) -> Unit,
    keyPrefix: String,
    selectedBankId: String? = null,
    showSelection: Boolean = false,
) {
    item(key = "$keyPrefix:top_spacer") {
        VSpacer(height = 12.dp)
    }
    itemsIndexed(
        items = banks,
        key = { _, bank -> "$keyPrefix:${bank.id}" },
    ) { index, bank ->
        PayCoreBankCell(
            index = index,
            itemsCount = banks.size,
            bank = bank,
            selected = bank.id == selectedBankId,
            showSelection = showSelection,
            onClick = { onBankClick(bank) },
        )
    }
    item(key = "$keyPrefix:bottom_spacer") {
        VSpacer(height = 12.dp)
    }
}

@Composable
private fun PayCoreBankCell(
    index: Int,
    itemsCount: Int,
    bank: PayCoreBankResponse,
    selected: Boolean,
    showSelection: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(getShape(itemsCount, index))
            .background(ComposeAppTheme.colors.lawrence)
    ) {
        SectionUniversalItem(
            borderTop = showDivider(itemsCount, index),
        ) {
            PayCoreBankRow(
                bank = bank,
                selected = selected,
                showSelection = showSelection,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun PayCoreBankRow(
    bank: PayCoreBankResponse,
    selected: Boolean,
    showSelection: Boolean,
    onClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        body_leah(
            text = bank.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSelection) {
            Spacer(modifier = Modifier.size(12.dp))
            HsRadioButton(selected = selected, onClick = onClick)
        } else {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun PayCoreBanksUnavailable(modifier: Modifier = Modifier) {
    PayCoreBanksMessage(textRes = R.string.paycore_banks_unavailable, modifier = modifier)
}

@Composable
internal fun PayCoreBanksNotFound(modifier: Modifier = Modifier) {
    PayCoreBanksMessage(textRes = R.string.EmptyResults, modifier = modifier)
}

@Composable
private fun PayCoreBanksMessage(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    body_grey(
        text = stringResource(textRes),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
    )
}

internal fun payCoreBankPreviewItems() = listOf(
    PayCoreBankResponse(id = "sber", name = "Sberbank"),
    PayCoreBankResponse(id = "tbank", name = "T-Bank"),
    PayCoreBankResponse(id = "alfa", name = "Alfa-Bank"),
)

package cash.p.terminal.ui.extensions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.HudHelper

@Composable
fun BottomSheetSelectorMultiple(
    config: BottomSheetSelectorMultipleDialog.Config,
    onItemsSelected: (List<Int>) -> Unit,
    onCloseClick: () -> Unit,
    maxListHeight: Dp = Dp.Unspecified,
    onSelectionChange: (List<Int>) -> Unit = {},
) {
    val selected = remember(config.uuid) { mutableStateListOf<Int>().apply { addAll(config.selectedIndexes) } }
    val onSelectedChange: (Int, Boolean) -> Unit = { index, checked ->
        if (checked) selected.add(index) else selected.remove(index)
        // Plain ArrayList: toList() returns Compose's persistent list, which a Bundle can't hold.
        onSelectionChange(ArrayList(selected))
    }

    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = config.icon.painter(),
            title = config.title,
            onCloseClick = onCloseClick
        ) {
            config.description?.let {
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    title = config.descriptionTitle,
                    text = it
                )
            }
            val listModifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ComposeAppTheme.colors.steel10, RoundedCornerShape(12.dp))

            val row: @Composable (Int, BottomSheetSelectorViewItem) -> Unit = { index, item ->
                val borderTop = index != 0
                item.header?.let { HeaderStick(borderTop = borderTop, text = it) }
                SelectorRow(
                    item = item,
                    borderTop = borderTop && item.header == null,
                    isSelected = selected.contains(index),
                    onSelectedChange = { checked -> onSelectedChange(index, checked) },
                )
            }
            if (maxListHeight == Dp.Unspecified) {
                Column(modifier = listModifier) {
                    config.viewItems.forEachIndexed { index, item -> row(index, item) }
                }
            } else {
                LazyColumn(modifier = listModifier.heightIn(max = maxListHeight)) {
                    itemsIndexed(config.viewItems) { index, item -> row(index, item) }
                }
            }
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                title = stringResource(R.string.Button_Done),
                onClick = {
                    onItemsSelected(selected)
                    onCloseClick.invoke()
                },
                enabled = config.allowEmpty || selected.isNotEmpty()
            )
        }
    }
}

@Composable
private fun SelectorRow(
    item: BottomSheetSelectorViewItem,
    borderTop: Boolean,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    val localView = LocalView.current
    val onClick = if (item.copyableString != null) {
        {
            HudHelper.showSuccessMessage(localView, R.string.Hud_Text_Copied)
            TextHelper.copyText(item.copyableString)
        }
    } else {
        null
    }

    SectionUniversalItem(
        borderTop = borderTop,
    ) {
        RowUniversal(
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalPadding = 0.dp
        ) {
            item.icon?.let { url ->
                HsImage(
                    url = url,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                body_leah(text = item.title)
                subhead2_grey(text = item.subtitle)
            }
            HsSwitch(
                modifier = Modifier.padding(start = 5.dp),
                checked = isSelected,
                onCheckedChange = onSelectedChange,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomSheetSelectorMultiplePreview() {
    val config = BottomSheetSelectorMultipleDialog.Config(
        title = "Select Options",
        description = "Please select the options you want.",
        descriptionTitle = "Options",
        viewItems = listOf(
            BottomSheetSelectorViewItem("Option 1", "Description 1", null, null),
            BottomSheetSelectorViewItem("Option 2", "Description 2", null, null),
            BottomSheetSelectorViewItem("Option 3", "Description super long to test component and how it works", null, null)
        ),
        selectedIndexes = listOf(0, 2),
        icon = ImageSource.Local(R.drawable.ic_attention_red_20),
        allowEmpty = true
    )

    BottomSheetSelectorMultiple(
        config = config,
        onItemsSelected = { /* Handle selection */ },
        onCloseClick = { /* Handle close */ }
    )
}
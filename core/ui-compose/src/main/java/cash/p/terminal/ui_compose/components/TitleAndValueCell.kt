package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.ColorName

@Composable
fun TitleAndValueCell(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 24.dp,
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        minHeight = minHeight
    ) {
        subhead2_grey(text = title, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        subhead1_leah(text = value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TitleAndValueClickableCell(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 24.dp,
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        minHeight = minHeight
    ) {
        subhead2_grey(text = title, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        subhead1_leah(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        )
    }
}

@Composable
fun TitleAndValueColoredCell(
    title: String,
    value: String,
    color: ColorName,
    modifier: Modifier = Modifier,
    minHeight: Dp = 24.dp,
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        minHeight = minHeight
    ) {
        subhead2_grey(text = title, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        when (color) {
            ColorName.Remus -> subhead1_remus(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ColorName.Lucian -> subhead1_lucian(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ColorName.Grey -> subhead1_grey(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ColorName.Leah -> subhead1_leah(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TitleAndTwoValuesCell(
    title: String,
    value: String,
    value2: String?,
    minHeight: Dp = 48.dp,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        minHeight = minHeight
    ) {
        subhead2_grey(text = title, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            subhead1_leah(text = value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value2 != null) {
                subhead2_grey(text = value2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
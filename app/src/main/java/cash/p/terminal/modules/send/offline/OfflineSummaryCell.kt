package cash.p.terminal.modules.send.offline

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

// Shared building blocks for the offline sign/broadcast summary sections: a section caption
// and a title/value row. Kept in one place so both flows render identical cells.
@Composable
internal fun SectionCaption(@StringRes titleResId: Int) {
    caption_grey(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        text = stringResource(titleResId),
    )
}

@Composable
internal fun OfflineSummaryCell(
    title: String,
    value: String,
    secondaryValue: String? = null,
    valueMaxLines: Int = 1,
    borderTop: Boolean = true,
) {
    QuoteInfoRow(
        borderTop = borderTop,
        title = {
            subhead2_grey(text = title)
        },
        value = {
            Column(horizontalAlignment = Alignment.End) {
                subhead2_leah(
                    text = value,
                    textAlign = TextAlign.End,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                secondaryValue?.let {
                    VSpacer(height = 1.dp)
                    subhead2_grey(
                        text = it,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

// Collapsible panel that reveals the full raw transaction hex on demand. Shared by the transfer
// and broadcast flows so both expose the hex with the same affordance.
@Composable
internal fun RawTransactionSection(rawHex: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .animateContentSize(),
    ) {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { expanded = !expanded },
        ) {
            subhead2_grey(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.offline_transaction_raw_hex),
            )
            Icon(
                modifier = Modifier.rotate(if (expanded) 0f else 180f),
                painter = painterResource(R.drawable.ic_arrow_big_up_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey,
            )
        }
        if (expanded) {
            SelectionContainer {
                body_leah(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    text = rawHex
                )
            }
        }
    }
}

// Centered circular status icon with a title and free-form content slot. Shared by the offline
// sync-error and broadcast-result screens so terminal states look the same across flows.
internal enum class OfflineStatusBlockStyle { Success, Error, Neutral }

@Composable
internal fun OfflineStatusBlock(
    @DrawableRes icon: Int,
    iconTint: Color,
    style: OfflineStatusBlockStyle,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(style.backgroundColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(icon),
                tint = iconTint,
                contentDescription = null,
            )
        }
        Spacer(Modifier.height(24.dp))
        headline2_leah(
            text = title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        content()
    }
}

@Composable
private fun OfflineStatusBlockStyle.backgroundColor(): Color =
    when (this) {
        OfflineStatusBlockStyle.Success -> ComposeAppTheme.colors.greenD.copy(alpha = 0.1f)
        OfflineStatusBlockStyle.Error -> ComposeAppTheme.colors.redD.copy(alpha = 0.1f)
        OfflineStatusBlockStyle.Neutral -> ComposeAppTheme.colors.lawrence
    }

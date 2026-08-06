package cash.p.terminal.modules.send.offline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsSettingCell

@Composable
internal fun OfflineSignActionCell(
    supported: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!supported) return

    Box(modifier = modifier.padding(top = 12.dp)) {
        CellUniversalLawrenceSection {
            HsSettingCell(
                title = R.string.offline_transaction_sign_offline,
                icon = R.drawable.ic_send_24,
                onClick = onClick,
                enabled = enabled,
            )
        }
    }
}

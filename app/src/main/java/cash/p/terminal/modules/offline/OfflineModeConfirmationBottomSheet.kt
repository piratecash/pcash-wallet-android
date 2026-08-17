package cash.p.terminal.modules.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.Coin
import kotlinx.coroutines.launch

private val MAX_MEMBERS_LIST_HEIGHT = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineModeConfirmationBottomSheet(
    blockchainName: String,
    isZcash: Boolean,
    members: List<OfflineModeAssetItem>,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.icon_warning_2_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(R.string.offline_mode_confirm_title),
            onCloseClick = close,
        ) {
            val description = if (isZcash) {
                stringResource(R.string.offline_mode_confirm_description_zcash)
            } else {
                stringResource(R.string.offline_mode_confirm_description, blockchainName)
            }
            subhead2_leah(modifier = Modifier.padding(horizontal = 32.dp), text = description)
            VSpacer(24.dp)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = MAX_MEMBERS_LIST_HEIGHT)) {
                items(members) { member -> OfflineMemberRow(member) }
            }
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(R.string.offline_mode_confirm_button),
                onClick = onConfirm,
                enabled = !inProgress,
                loadingIndicator = inProgress,
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_Cancel),
                onClick = close,
                enabled = !inProgress,
            )
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun OfflineMemberRow(member: OfflineModeAssetItem) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .clip(shape)
            .background(ComposeAppTheme.colors.lawrence)
            .border(1.dp, ComposeAppTheme.colors.steel20, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(coin = member.coin, modifier = Modifier.size(24.dp))
        HSpacer(12.dp)
        body_leah(text = member.coin.code)
        member.badge?.let {
            Badge(modifier = Modifier.padding(start = 8.dp), text = it)
        }
        Spacer(Modifier.weight(1f))
        body_leah(text = member.balance)
    }
}

private fun previewMember(code: String, badge: String?, balance: String) = OfflineModeAssetItem(
    coin = Coin(uid = code, name = code, code = code),
    badge = badge,
    balance = balance,
)

@Preview(name = "Short list")
@Composable
private fun OfflineModeConfirmationBottomSheetShortPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "Ethereum",
            isZcash = false,
            members = listOf(
                previewMember("USDT", "ERC20", "1 234.56"),
                previewMember("USDC", "ERC20", "500.00"),
            ),
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Long list")
@Composable
private fun OfflineModeConfirmationBottomSheetLongPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "BNB Smart Chain",
            isZcash = false,
            members = (1..8).map { previewMember("TOKEN$it", "BEP20", "$it.00") },
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Zcash")
@Composable
private fun OfflineModeConfirmationBottomSheetZcashPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "Zcash",
            isZcash = true,
            members = listOf(
                previewMember("ZEC", "Transparent", "0.5"),
                previewMember("ZEC", "Unified", "1.2"),
                previewMember("ZEC", "Shielded", "3.4"),
            ),
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

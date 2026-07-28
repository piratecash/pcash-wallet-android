package cash.p.terminal.modules.zcashmigration

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Whole Orchard -> Ironwood migration flow: the offer sheet followed by the confirmation screen.
 * Rendered in place by the entry point, so no navigation destination is involved.
 */
@Composable
internal fun ZcashMigrationFlow(
    wallet: Wallet,
    onClose: () -> Unit,
) {
    // Remembered before the lock gate below: an early return drops every slot after it, and
    // forgetting this one would re-offer a migration whose send is already running.
    var confirming by remember { mutableStateOf(false) }

    // Both the sheet and the dialog render in their own windows above the in-activity lock
    // overlay, which only dismisses DialogFragments. Without this gate the balance and the
    // Migrate button would stay visible and tappable over the PIN screen.
    val pinComponent = remember { getKoinInstance<IPinComponent>() }
    val isLocked by pinComponent.isLockedFlow.collectAsStateWithLifecycle()
    if (isLocked) return

    if (confirming) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // The view model store belongs to the enclosing fragment, so an unkeyed view model
            // would keep the wallet and adapter of whichever account opened the screen first.
            val viewModel = koinViewModel<ZcashMigrationViewModel>(
                key = "${wallet.account.id}:${wallet.tokenQueryId}"
            ) { parametersOf(wallet) }
            // The view model outlives the dialog, so a reopened screen would otherwise show the
            // proposal and the send result of the previous attempt.
            LaunchedEffect(viewModel) { viewModel.prepare() }
            ZcashMigrationConfirmScreen(
                uiState = viewModel.uiState,
                coin = wallet.coin,
                onMigrateClick = viewModel::onClickMigrate,
                onClose = onClose,
            )
        }
    } else {
        ZcashMigrationOfferSheet(
            onMigrate = { confirming = true },
            onDismiss = onClose,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZcashMigrationOfferSheet(
    onMigrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.balance_zcash_migration_title),
            text = stringResource(R.string.balance_zcash_migration_description),
            iconPainter = painterResource(R.drawable.ic_migrate_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            confirmText = stringResource(R.string.balance_zcash_migration_migrate),
            cautionType = Caution.Type.Warning,
            cancelText = stringResource(R.string.Button_Cancel),
            onConfirm = {
                scope.launch {
                    sheetState.hide()
                    onMigrate()
                }
            },
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }
        )
    }
}

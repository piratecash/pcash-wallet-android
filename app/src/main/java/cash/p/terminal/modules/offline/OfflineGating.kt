package cash.p.terminal.modules.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

fun IWalletManager.walletFor(token: Token): Wallet? =
    activeWallets.firstOrNull { it.token == token }

fun IWalletManager.walletFor(blockchainType: BlockchainType): Wallet? =
    activeWallets.firstOrNull { it.token.blockchainType == blockchainType }

/** Like [OfflineOperationGate.availability], but falls back to [OperationAvailability.Unavailable] when no wallet was resolved. */
fun OfflineOperationGate.availabilityFor(wallet: Wallet?, enabled: Boolean): OperationAvailability =
    if (wallet == null) OperationAvailability.Unavailable else availability(wallet, enabled)

/** Defers a click behind [OfflineBlockedBottomSheet] when blocked, and replays it once the user goes online. */
@Composable
fun rememberOfflineGatedAction(wallet: Wallet?): OfflineGatedAction {
    val currentWallet = rememberUpdatedState(wallet)
    return remember { OfflineGatedAction(currentWallet) }
}

class OfflineGatedAction internal constructor(private val wallet: State<Wallet?>) {
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun onClick(availability: OperationAvailability, action: () -> Unit) {
        if (availability == OperationAvailability.BlockedOffline && wallet.value != null) {
            pendingAction = action
        } else {
            action()
        }
    }

    @Composable
    fun Sheet() {
        val currentWallet = wallet.value ?: return
        val action = pendingAction ?: return
        OfflineBlockedBottomSheet(
            wallet = currentWallet,
            onWentOnline = {
                pendingAction = null
                action()
            },
            onDismiss = { pendingAction = null },
        )
    }
}

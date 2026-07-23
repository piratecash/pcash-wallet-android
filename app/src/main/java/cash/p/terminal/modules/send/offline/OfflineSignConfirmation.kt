package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.navigation.popBackStackSafely
import org.koin.compose.koinInject

/**
 * Whether the confirmation screen must replace the normal "Send" flow with the
 * offline-sign blocker ([OfflineSendSyncErrorScreen]): on a real network loss
 * ([isConnected] is false). A sync problem while the network is present
 * (kit resyncing / bad node) stays on the confirm screen.
 */
internal fun shouldShowOfflineSyncBlocker(
    offlineSignSupported: Boolean,
    isConnected: Boolean,
): Boolean = offlineSignSupported && !isConnected

/**
 * Whether the wallet was in a good ([isSynced] && connected) state within the grace window.
 * A brief blip keeps the Send button enabled and suppresses the offline blocker.
 */
internal fun isWithinSyncGrace(
    lastGoodElapsedMs: Long?,
    nowElapsedMs: Long,
    graceMs: Long,
): Boolean = lastGoodElapsedMs != null && (nowElapsedMs - lastGoodElapsedMs) < graceMs

/**
 * Whether the blocker's retry indicator should spin: either a local retry was just
 * triggered, or an adapter sync retry is running against a reachable, non-errored network.
 */
internal fun isOfflineRetryInProgress(
    retrying: Boolean,
    syncRetrying: Boolean,
    isConnected: Boolean,
    hasAdapterError: Boolean,
): Boolean = retrying || (syncRetrying && isConnected && !hasAdapterError)

/**
 * Shared confirmation host used by every send chain. It owns the inner NavHost and the
 * offline-sign flow routes, and gates the online confirmation ([onlineContent]) behind
 * the offline blocker whenever [shouldShowOfflineSyncBlocker] holds. This is the single
 * algorithm all chains follow; only [onlineContent] and the per-chain route ids differ.
 *
 * [sourceChangeable]/[onChangeSourceClick] carry Bitcoin's "Change Source" capability;
 * other chains pass `false` / `{}`.
 */
@Composable
internal fun OfflineSignableConfirmationHost(
    fragmentNavController: NavController,
    sendViewModel: OfflineSignCapableViewModel,
    confirmationRoute: String,
    signFlowRoutes: OfflineSignFlowRoutes,
    sourceChangeable: Boolean,
    onChangeSourceClick: () -> Unit,
    onlineContent: @Composable (onRequestOfflineSign: (() -> Unit)?) -> Unit,
) {
    val composeNavController = rememberNavController()
    NavHost(
        navController = composeNavController,
        startDestination = confirmationRoute,
    ) {
        composable(confirmationRoute) {
            OfflineSignableConfirmationContent(
                fragmentNavController = fragmentNavController,
                composeNavController = composeNavController,
                sendViewModel = sendViewModel,
                signRoute = signFlowRoutes.signRoute,
                sourceChangeable = sourceChangeable,
                onChangeSourceClick = onChangeSourceClick,
                onlineContent = onlineContent,
            )
        }
        offlineSignFlowRoutes(
            routes = signFlowRoutes,
            navController = composeNavController,
            fragmentNavController = fragmentNavController,
            sendViewModel = sendViewModel,
        )
    }
}

@Composable
private fun OfflineSignableConfirmationContent(
    fragmentNavController: NavController,
    composeNavController: NavHostController,
    sendViewModel: OfflineSignCapableViewModel,
    signRoute: String,
    sourceChangeable: Boolean,
    onChangeSourceClick: () -> Unit,
    onlineContent: @Composable (onRequestOfflineSign: (() -> Unit)?) -> Unit,
) {
    val connectivityManager = koinInject<IConnectivityManager>()
    val isConnected by connectivityManager.isConnected.collectAsStateWithLifecycle()
    var retrying by remember { mutableStateOf(false) }

    val showSyncBlocker = shouldShowOfflineSyncBlocker(
        offlineSignSupported = sendViewModel.offlineSignSupported,
        isConnected = isConnected,
    )
    val retryInProgress = isOfflineRetryInProgress(
        retrying = retrying,
        syncRetrying = sendViewModel.syncRetrying,
        isConnected = isConnected,
        hasAdapterError = sendViewModel.hasAdapterError,
    )

    OfflineSyncRetryProgressEffect(
        retrying = retrying,
        isConnected = isConnected,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetryFinish = { retrying = false },
    )

    if (showSyncBlocker) {
        val coinCode = sendViewModel.wallet.coin.code
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = stringResource(R.string.Send_Title, coinCode),
                coinCode = coinCode,
                noConnection = !isConnected,
                inProgress = retryInProgress,
                sourceChangeable = sourceChangeable,
            ),
            callbacks = OfflineSendSyncErrorCallbacks(
                onBackClick = fragmentNavController::popBackStackSafely,
                onRetryClick = {
                    retrying = true
                    sendViewModel.retryAdapterSync()
                },
                onChangeSourceClick = onChangeSourceClick,
                onSignOfflineClick = { composeNavController.navigate(signRoute) },
            ),
        )
        return
    }

    // Only offer offline signing after a send failure that happened while offline — a balance /
    // validation / RPC failure (network present) is not solved by offline signing.
    val onRequestOfflineSign: (() -> Unit)? = if (sendViewModel.offlineSignSupported && !isConnected) {
        { composeNavController.navigate(signRoute) }
    } else {
        null
    }
    onlineContent(onRequestOfflineSign)
}

package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val RetryProgressMinVisibleMillis = 1200L

internal data class OfflineSignRouteState(
    val confirmationData: SendConfirmationData,
    val blockchainName: String,
    val coinMaxAllowedDecimals: Int,
    val feeCoinMaxAllowedDecimals: Int,
    val rate: CurrencyValue?,
    val signState: OfflineSignState,
)

internal data class OfflineSignFlowRoutes(
    val signRoute: String,
    val transferRoute: String,
    val transferFormatArgument: String,
)

internal interface OfflineSignCapableViewModel {
    val wallet: Wallet
    val coinMaxAllowedDecimals: Int
    val feeCoinMaxAllowedDecimals: Int
    val coinRate: CurrencyValue?
    val offlineSigningController: OfflineSigningController<*>

    val offlineSignState: OfflineSignState
        get() = offlineSigningController.signState
    val offlineSignedTransaction: OfflineSignedTransaction?
        get() = offlineSigningController.signedTransaction

    fun getConfirmationData(): SendConfirmationData
    fun onClickSignOffline(format: OfflineTransactionFormat)

    fun resetOfflineSignState() = offlineSigningController.resetSignState()
    fun onOfflineTransferClosed() = offlineSigningController.closeTransfer()
}

internal fun NavGraphBuilder.offlineSignFlowRoutes(
    routes: OfflineSignFlowRoutes,
    navController: NavHostController,
    fragmentNavController: NavController,
    sendViewModel: OfflineSignCapableViewModel,
) {
    offlineSignRoute(
        route = routes.signRoute,
        navController = navController,
        stateProvider = { sendViewModel.offlineSignRouteState() },
        onLeave = {
            sendViewModel.resetOfflineSignState()
            navController.popBackStackSafely()
        },
        onSignClick = sendViewModel::onClickSignOffline,
        onSignStateConsumed = sendViewModel::resetOfflineSignState,
        onSigned = { format ->
            navController.navigate(offlineTransactionTransferRoute(routes.transferRoute, format))
        },
    )
    offlineTransactionTransferRoute(
        route = routes.transferRoute,
        formatArgument = routes.transferFormatArgument,
        navController = navController,
        transactionProvider = { sendViewModel.offlineSignedTransaction },
        onDoneClick = {
            sendViewModel.onOfflineTransferClosed()
            fragmentNavController.popBackStack(R.id.sendXFragment, true)
        },
    )
}

private fun OfflineSignCapableViewModel.offlineSignRouteState(): OfflineSignRouteState? =
    tryOrNull { getConfirmationData() }?.let { confirmationData ->
        OfflineSignRouteState(
            confirmationData = confirmationData,
            blockchainName = wallet.token.blockchain.name,
            coinMaxAllowedDecimals = coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = feeCoinMaxAllowedDecimals,
            rate = coinRate,
            signState = offlineSignState,
        )
    }

internal fun NavGraphBuilder.offlineSignRoute(
    route: String,
    navController: NavHostController,
    stateProvider: () -> OfflineSignRouteState?,
    onLeave: () -> Unit,
    onSignClick: (OfflineTransactionFormat) -> Unit,
    onSignStateConsumed: () -> Unit,
    onSigned: (OfflineTransactionFormat) -> Unit,
) {
    composablePage(route) {
        val state = stateProvider()
        if (state == null) {
            LaunchedEffect(Unit) {
                navController.popBackStack()
            }
            return@composablePage
        }

        OfflineSignScreen(
            confirmationData = state.confirmationData,
            blockchainName = state.blockchainName,
            coinMaxAllowedDecimals = state.coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = state.feeCoinMaxAllowedDecimals,
            rate = state.rate,
            signState = state.signState,
            callbacks = OfflineSignCallbacks(
                onBackClick = onLeave,
                onCancelClick = onLeave,
                onSignClick = onSignClick,
                onSignStateConsumed = onSignStateConsumed,
                onSigned = onSigned,
            ),
        )
    }
}

internal fun NavGraphBuilder.offlineTransactionTransferRoute(
    route: String,
    formatArgument: String,
    navController: NavHostController,
    transactionProvider: () -> OfflineSignedTransaction?,
    onDoneClick: () -> Unit,
) {
    composablePage(
        route = "$route/{$formatArgument}",
        arguments = listOf(
            navArgument(formatArgument) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val qrCodeSaver: OfflineQrCodeSaver = koinInject()
        val initialFormat = backStackEntry.arguments
            ?.getString(formatArgument)
            .toOfflineTransactionFormat()
        OfflineTransactionTransferScreen(
            transaction = transactionProvider(),
            selectedFormat = initialFormat,
            qrCodeSaver = qrCodeSaver,
            onBackClick = navController::popBackStackSafely,
            onDoneClick = onDoneClick,
        )
    }
}

internal fun offlineTransactionTransferRoute(route: String, format: OfflineTransactionFormat): String =
    "$route/${format.name}"

private fun String?.toOfflineTransactionFormat(): OfflineTransactionFormat =
    OfflineTransactionFormat.entries.firstOrNull { it.name == this } ?: OfflineTransactionFormat.Pcash

@Composable
internal fun OfflineSyncRetryProgressEffect(
    retrying: Boolean,
    isConnected: Boolean,
    isSynced: Boolean,
    hasAdapterError: Boolean,
    onRetryFinish: () -> Unit,
) {
    val currentOnRetryFinish = rememberUpdatedState(onRetryFinish)
    LaunchedEffect(retrying, isConnected, hasAdapterError, isSynced) {
        if (!retrying) return@LaunchedEffect

        when {
            isConnected && isSynced && !hasAdapterError -> currentOnRetryFinish.value()
            !isConnected || hasAdapterError -> {
                delay(RetryProgressMinVisibleMillis)
                currentOnRetryFinish.value()
            }
        }
    }
}

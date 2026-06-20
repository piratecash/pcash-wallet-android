package cash.p.terminal.modules.send.bitcoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineBitcoinSignCallbacks
import cash.p.terminal.modules.send.offline.OfflineBitcoinSignScreen
import cash.p.terminal.modules.send.offline.OfflineQrCodeSaver
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorCallbacks
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorScreen
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.send.offline.OfflineTransactionTransferScreen
import cash.p.terminal.modules.syncerror.SyncErrorModule
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromBottom
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val BitcoinConfirmationPage = "bitcoin_confirmation"
private const val OfflineBitcoinSignPage = "offline_bitcoin_sign"
private const val OfflineTransactionTransferPage = "offline_transaction_transfer"
private const val OfflineTransactionTransferFormatArg = "format"
private const val OfflineTransactionTransferRoute =
    "$OfflineTransactionTransferPage/{$OfflineTransactionTransferFormatArg}"
private const val RetryProgressMinVisibleMillis = 1200L

@Composable
fun SendBitcoinConfirmationScreen(
    navController: NavController,
    sendViewModel: SendBitcoinViewModel,
    sendEntryPointDestId: Int
) {
    val navState = BitcoinConfirmationNavState(
        fragmentNavController = navController,
        composeNavController = rememberNavController(),
        sendEntryPointDestId = sendEntryPointDestId,
    )
    NavHost(
        navController = navState.composeNavController,
        startDestination = BitcoinConfirmationPage,
    ) {
        bitcoinConfirmationRoute(navState, sendViewModel)
        offlineBitcoinSignRoute(navState, sendViewModel)
        offlineTransactionTransferRoute(navState, sendViewModel)
    }
}

private fun NavGraphBuilder.bitcoinConfirmationRoute(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    composable(BitcoinConfirmationPage) {
        SendBitcoinConfirmationContent(
            navState = navState,
            sendViewModel = sendViewModel,
        )
    }
}

private fun NavGraphBuilder.offlineBitcoinSignRoute(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    composablePage(OfflineBitcoinSignPage) {
        val confirmationData = remember(sendViewModel) {
            tryOrNull { sendViewModel.getConfirmationData() }
        }
        if (confirmationData == null) {
            LaunchedEffect(Unit) {
                navState.composeNavController.popBackStackSafely()
            }
        } else {
            // Leaving the sign screen must abort any in-flight signing so a late hardware result cannot
            // resurrect the Signed state after the user backed out.
            val onLeave: () -> Unit = {
                sendViewModel.resetOfflineSignState()
                navState.composeNavController.popBackStackSafely()
            }
            OfflineBitcoinSignScreen(
                confirmationData = confirmationData,
                blockchainName = sendViewModel.wallet.token.blockchain.name,
                coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
                rate = sendViewModel.coinRate,
                signState = sendViewModel.offlineSignState,
                callbacks = OfflineBitcoinSignCallbacks(
                    onBackClick = onLeave,
                    onCancelClick = onLeave,
                    onSignClick = sendViewModel::onClickSignOffline,
                    onSignStateConsumed = sendViewModel::resetOfflineSignState,
                    onSigned = { format ->
                        navState.composeNavController.navigate(offlineTransactionTransferRoute(format))
                    },
                ),
            )
        }
    }
}

private fun NavGraphBuilder.offlineTransactionTransferRoute(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    composablePage(
        route = OfflineTransactionTransferRoute,
        arguments = listOf(
            navArgument(OfflineTransactionTransferFormatArg) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val qrCodeSaver: OfflineQrCodeSaver = koinInject()
        val initialFormat = backStackEntry.arguments
            ?.getString(OfflineTransactionTransferFormatArg)
            .toOfflineTransactionFormat()
        OfflineTransactionTransferScreen(
            transaction = sendViewModel.offlineSignedTransaction,
            selectedFormat = initialFormat,
            qrCodeSaver = qrCodeSaver,
            onBackClick = navState.composeNavController::popBackStackSafely,
            onDoneClick = {
                sendViewModel.onOfflineTransferClosed()
                navState.fragmentNavController.popBackStack(R.id.sendXFragment, true)
            },
        )
    }
}

@Composable
private fun SendBitcoinConfirmationContent(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    val isConnected by App.connectivityManager.isConnected.collectAsStateWithLifecycle()
    val syncErrorViewModel = viewModel<SyncErrorViewModel>(
        factory = SyncErrorModule.Factory(sendViewModel.wallet)
    )
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }
    val showSyncBlocker = sendViewModel.offlineSignSupported &&
            (!isConnected || !sendViewModel.isSynced || sendViewModel.hasAdapterError)
    val retryInProgress = retrying ||
            (sendViewModel.syncRetrying && isConnected && !sendViewModel.hasAdapterError)

    LifecycleResumeEffect(sendViewModel) {
        if (refresh) {
            confirmationData = sendViewModel.getConfirmationData()
        }

        onPauseOrDispose {
            refresh = true
        }
    }

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) {
            confirmationData = sendViewModel.getConfirmationData()
        }
    }

    SyncRetryProgressEffect(
        retrying = retrying,
        isConnected = isConnected,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetryFinish = { retrying = false },
    )

    if (showSyncBlocker) {
        BitcoinOfflineSyncBlocker(
            navState = navState,
            coinCode = sendViewModel.wallet.coin.code,
            noConnection = !isConnected,
            inProgress = retryInProgress,
            sourceChangeable = syncErrorViewModel.sourceChangeable,
            blockchainWrapper = syncErrorViewModel.blockchainWrapper,
            onRetryClick = {
                retrying = true
                sendViewModel.retryAdapterSync()
            },
        )
        return
    }

    BitcoinOnlineConfirmation(
        navState = navState,
        sendViewModel = sendViewModel,
        confirmationData = confirmationData,
    )
}

@Composable
private fun BitcoinOnlineConfirmation(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
    confirmationData: SendConfirmationData,
) {
    SendConfirmationScreen(
        navController = navState.fragmentNavController,
        coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.coinRate,
        sendResult = sendViewModel.sendResult,
        blockchainType = sendViewModel.blockchainType,
        coin = confirmationData.coin,
        feeCoin = confirmationData.coin,
        amount = confirmationData.amount,
        address = confirmationData.address,
        contact = confirmationData.contact,
        fee = confirmationData.fee,
        lockTimeInterval = confirmationData.lockTimeInterval,
        memo = confirmationData.memo,
        rbfEnabled = confirmationData.rbfEnabled,
        onClickSend = sendViewModel::onClickSend,
        sendEntryPointDestId = navState.sendEntryPointDestId,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetrySync = sendViewModel::retryAdapterSync,
        sendToken = sendViewModel.wallet.token,
        feeToken = sendViewModel.feeToken,
        feeCoinBalance = sendViewModel.feeCoinBalance,
        displayBalance = sendViewModel.displayBalance,
        insufficientFeeBalance = sendViewModel.isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = sendViewModel.balanceHidden,
        onBalanceClicked = sendViewModel::toggleHideBalance,
    )
}

@Composable
private fun SyncRetryProgressEffect(
    retrying: Boolean,
    isConnected: Boolean,
    isSynced: Boolean,
    hasAdapterError: Boolean,
    onRetryFinish: () -> Unit,
) {
    val currentOnRetryFinish by rememberUpdatedState(onRetryFinish)
    LaunchedEffect(retrying, isConnected, hasAdapterError, isSynced) {
        if (!retrying) return@LaunchedEffect

        when {
            isConnected && isSynced && !hasAdapterError -> currentOnRetryFinish()
            !isConnected || hasAdapterError -> {
                delay(RetryProgressMinVisibleMillis)
                currentOnRetryFinish()
            }
        }
    }
}

@Composable
private fun BitcoinOfflineSyncBlocker(
    navState: BitcoinConfirmationNavState,
    coinCode: String,
    noConnection: Boolean,
    inProgress: Boolean,
    sourceChangeable: Boolean,
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
    onRetryClick: () -> Unit,
) {
    OfflineSendSyncErrorScreen(
        state = OfflineSendSyncErrorState(
            title = stringResource(R.string.Send_Title, coinCode),
            coinCode = coinCode,
            noConnection = noConnection,
            inProgress = inProgress,
            sourceChangeable = sourceChangeable,
        ),
        callbacks = OfflineSendSyncErrorCallbacks(
            onBackClick = navState.fragmentNavController::popBackStackSafely,
            onRetryClick = onRetryClick,
            onChangeSourceClick = {
                navState.fragmentNavController.openBitcoinSourceSettings(blockchainWrapper)
            },
            onSignOfflineClick = {
                navState.composeNavController.openOfflineBitcoinSign()
            },
        ),
    )
}

private data class BitcoinConfirmationNavState(
    val fragmentNavController: NavController,
    val composeNavController: NavHostController,
    val sendEntryPointDestId: Int,
)

private fun NavController.openBitcoinSourceSettings(
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
) {
    if (blockchainWrapper?.type != SyncErrorModule.BlockchainWrapper.Type.Bitcoin) return

    slideFromBottom(
        R.id.btcBlockchainSettingsFragment,
        blockchainWrapper.blockchain
    )
}

private fun NavHostController.openOfflineBitcoinSign() {
    navigate(OfflineBitcoinSignPage)
}

private fun offlineTransactionTransferRoute(format: OfflineTransactionFormat): String =
    "$OfflineTransactionTransferPage/${format.name}"

private fun String?.toOfflineTransactionFormat(): OfflineTransactionFormat =
    OfflineTransactionFormat.entries.firstOrNull { it.name == this } ?: OfflineTransactionFormat.Pcash

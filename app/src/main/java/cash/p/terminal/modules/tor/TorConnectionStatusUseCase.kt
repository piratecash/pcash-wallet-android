package cash.p.terminal.modules.tor

import cash.p.terminal.R
import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.manager.ITorConnectionStatusUseCase
import cash.p.terminal.manager.ITorConnectionStatusUseCase.*
import cash.p.terminal.manager.TorViewState
import cash.p.terminal.modules.settings.security.tor.TorStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TorConnectionStatusUseCase(
    private val torManager: ITorManager,
    private val connectivityManager: ConnectivityManager
): ITorConnectionStatusUseCase {

    override val torViewState: Flow<TorViewState> = torManager.torStatusFlow
        .map { torStatus ->
            createTorViewState(torStatus)
        }

    override fun restartTor(): RestartResult {
        return if (connectivityManager.isConnected.value) {
            torManager.start()
            RestartResult.Success
        } else {
            RestartResult.NoNetworkConnection
        }
    }

    private fun createTorViewState(torStatus: TorStatus): TorViewState {
        val isConnected = connectivityManager.isConnected.value
        val torIsActive = torStatus == TorStatus.Connected
        val showRetryButton = torStatus == TorStatus.Failed
        val showNoNetworkConnectionError = torStatus != TorStatus.Connected && !isConnected

        val stateText = when (torStatus) {
            TorStatus.Connected -> R.string.Tor_TorIsActive
            TorStatus.Failed -> R.string.TorPage_Failed
            else -> R.string.TorPage_Connecting
        }

        return TorViewState(
            stateText = stateText,
            showRetryButton = showRetryButton,
            torIsActive = torIsActive,
            showNetworkConnectionError = showNoNetworkConnectionError
        )
    }
}


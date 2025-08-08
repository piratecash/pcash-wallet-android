package cash.p.terminal.manager

import kotlinx.coroutines.flow.Flow

interface ITorConnectionStatusUseCase {

    val torViewState: Flow<TorViewState>

    fun restartTor(): RestartResult

    sealed class RestartResult {
        object Success : RestartResult()
        object NoNetworkConnection : RestartResult()
    }
}

data class TorViewState(
    val stateText: Int,
    val showRetryButton: Boolean,
    val torIsActive: Boolean,
    val showNetworkConnectionError: Boolean,
)
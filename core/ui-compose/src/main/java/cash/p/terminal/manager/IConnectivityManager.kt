package cash.p.terminal.manager

import kotlinx.coroutines.flow.StateFlow

interface IConnectivityManager {
    val isConnected: StateFlow<Boolean>
    val torEnabled: Boolean
}
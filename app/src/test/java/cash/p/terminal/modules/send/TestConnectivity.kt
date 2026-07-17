package cash.p.terminal.modules.send

import cash.p.terminal.manager.IConnectivityManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A relaxed [IConnectivityManager] mock for tests: `BaseSendViewModel` now observes connectivity, so
 * every test that constructs a send / swap-confirm view model must bind one via Koin.
 */
fun mockConnectivityManager(
    isConnected: StateFlow<Boolean> = MutableStateFlow(true),
): IConnectivityManager = mockk(relaxed = true) {
    every { this@mockk.isConnected } returns isConnected
}

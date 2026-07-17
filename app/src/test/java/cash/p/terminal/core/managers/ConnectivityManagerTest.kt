package cash.p.terminal.core.managers

import android.net.ConnectivityManager as AndroidConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the stale-`isConnected` bug: the value must not stay `true` after the
 * device goes offline. Two safety nets are verified — the foreground revalidation timer
 * (self-heals a missed loss event) and the single retry after a failed callback registration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ConnectivityManagerTest {

    // Mirrors the private production constants (kept in sync intentionally).
    private val revalidateIntervalMs = 15_000L
    private val callbackRetryDelayMs = 2_000L

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))

    private val bgState = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
    private val backgroundManager = mockk<BackgroundManager> {
        every { stateFlow } returns bgState
    }
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val keepAliveManager = mockk<BackgroundKeepAliveManager>(relaxed = true)
    private val systemConnectivityManager = mockk<AndroidConnectivityManager>(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun foreground_validatedNetwork_isConnectedTrue() {
        every { systemConnectivityManager.activeNetwork } returns validatedNetwork()
        val manager = createManager()

        enterForeground()

        assertTrue(manager.isConnected.value)
    }

    @Test
    fun revalidateTimer_networkLostWithoutCallback_flipsToDisconnected() {
        every { systemConnectivityManager.activeNetwork } returns validatedNetwork()
        val manager = createManager()
        enterForeground()
        assertTrue(manager.isConnected.value)

        // Network drops, but no onLost/onUnavailable callback is delivered (the observed bug).
        every { systemConnectivityManager.activeNetwork } returns null
        assertTrue(manager.isConnected.value) // still stale-true until the timer fires

        scheduler.advanceTimeBy(revalidateIntervalMs + 1)
        scheduler.runCurrent()

        assertFalse(manager.isConnected.value) // re-derived from the system → offline
    }

    @Test
    fun registerCallback_firstAttemptFails_retriesExactlyOnce() {
        var attempts = 0
        every {
            systemConnectivityManager.registerNetworkCallback(
                any(),
                any<AndroidConnectivityManager.NetworkCallback>()
            )
        } answers {
            attempts++
            if (attempts == 1) error("TooManyRequests")
        }
        every { systemConnectivityManager.activeNetwork } returns null

        createManager()
        enterForeground()
        verify(exactly = 1) {
            systemConnectivityManager.registerNetworkCallback(any(), any<AndroidConnectivityManager.NetworkCallback>())
        }

        scheduler.advanceTimeBy(callbackRetryDelayMs + 1)
        scheduler.runCurrent()
        verify(exactly = 2) {
            systemConnectivityManager.registerNetworkCallback(any(), any<AndroidConnectivityManager.NetworkCallback>())
        }

        // The retry is single: no further attempts even after more time passes.
        scheduler.advanceTimeBy(callbackRetryDelayMs * 3)
        scheduler.runCurrent()
        verify(exactly = 2) {
            systemConnectivityManager.registerNetworkCallback(any(), any<AndroidConnectivityManager.NetworkCallback>())
        }
    }

    private fun createManager(): ConnectivityManager {
        val manager = ConnectivityManager(
            backgroundManager,
            localStorage,
            keepAliveManager,
            systemConnectivityManager,
            dispatcherProvider,
        )
        scheduler.runCurrent() // start the internal collectors + process the init refresh
        return manager
    }

    private fun enterForeground() {
        bgState.value = BackgroundManagerState.EnterForeground
        scheduler.runCurrent()
    }

    private fun validatedNetwork(): Network {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        }
        every { systemConnectivityManager.getNetworkCapabilities(network) } returns capabilities
        return network
    }
}

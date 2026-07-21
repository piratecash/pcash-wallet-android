package cash.p.terminal.modules.send.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSignGateTest {

    // region shouldShowOfflineSyncBlocker — blocker on real network loss

    @Test
    fun shouldShowOfflineSyncBlocker_notSupported_returnsFalse() {
        assertFalse(shouldShowOfflineSyncBlocker(false, isConnected = false))
    }

    @Test
    fun shouldShowOfflineSyncBlocker_supportedOffline_returnsTrue() {
        assertTrue(shouldShowOfflineSyncBlocker(true, isConnected = false))
    }

    @Test
    fun shouldShowOfflineSyncBlocker_supportedConnected_returnsFalse() {
        // Network present (even while the kit resyncs) → never the offline blocker.
        assertFalse(shouldShowOfflineSyncBlocker(true, isConnected = true))
    }

    // endregion

    // region isWithinSyncGrace

    @Test
    fun isWithinSyncGrace_neverGood_returnsFalse() {
        assertFalse(isWithinSyncGrace(lastGoodElapsedMs = null, nowElapsedMs = 1000L, graceMs = 500L))
    }

    @Test
    fun isWithinSyncGrace_withinWindow_returnsTrue() {
        assertTrue(isWithinSyncGrace(lastGoodElapsedMs = 1000L, nowElapsedMs = 1400L, graceMs = 500L))
    }

    @Test
    fun isWithinSyncGrace_atBoundary_returnsFalse() {
        assertFalse(isWithinSyncGrace(lastGoodElapsedMs = 1000L, nowElapsedMs = 1500L, graceMs = 500L))
    }

    @Test
    fun isWithinSyncGrace_expired_returnsFalse() {
        assertFalse(isWithinSyncGrace(lastGoodElapsedMs = 1000L, nowElapsedMs = 5000L, graceMs = 500L))
    }

    // endregion

    // region isOfflineRetryInProgress (unchanged)

    @Test
    fun isOfflineRetryInProgress_localRetrying_returnsTrue() {
        assertTrue(isOfflineRetryInProgress(retrying = true, syncRetrying = false, isConnected = false, hasAdapterError = true))
    }

    @Test
    fun isOfflineRetryInProgress_syncRetryingConnectedNoError_returnsTrue() {
        assertTrue(isOfflineRetryInProgress(retrying = false, syncRetrying = true, isConnected = true, hasAdapterError = false))
    }

    @Test
    fun isOfflineRetryInProgress_syncRetryingButOffline_returnsFalse() {
        assertFalse(isOfflineRetryInProgress(retrying = false, syncRetrying = true, isConnected = false, hasAdapterError = false))
    }

    @Test
    fun isOfflineRetryInProgress_idle_returnsFalse() {
        assertFalse(isOfflineRetryInProgress(retrying = false, syncRetrying = false, isConnected = true, hasAdapterError = false))
    }

    // endregion
}

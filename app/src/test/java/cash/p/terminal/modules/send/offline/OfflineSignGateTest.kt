package cash.p.terminal.modules.send.offline

import cash.p.terminal.modules.send.SendErrorLowFee
import cash.p.terminal.modules.send.SendResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSignGateTest {

    // region shouldShowOfflineSyncBlocker — blocker on real network loss

    @Test
    fun shouldShowOfflineSyncBlocker_notSupported_returnsFalse() {
        assertFalse(shouldShowOfflineSyncBlocker(false, isConnected = false, sendResult = null))
    }

    @Test
    fun shouldShowOfflineSyncBlocker_supportedOffline_returnsTrue() {
        assertTrue(shouldShowOfflineSyncBlocker(true, isConnected = false, sendResult = null))
    }

    @Test
    fun shouldShowOfflineSyncBlocker_supportedConnected_returnsFalse() {
        // Network present (even while the kit resyncs) → never the offline blocker.
        assertFalse(shouldShowOfflineSyncBlocker(true, isConnected = true, sendResult = null))
    }

    @Test
    fun shouldShowOfflineSyncBlocker_sending_returnsFalse() {
        // A send is in flight: swapping the confirmation screen for the blocker hides its
        // outcome and offers retry / offline-sign buttons that can broadcast a second time.
        assertFalse(
            shouldShowOfflineSyncBlocker(true, isConnected = false, sendResult = SendResult.Sending)
        )
    }

    @Test
    fun shouldShowOfflineSyncBlocker_sendFailed_returnsFalse() {
        // The failure caution and the offline-sign prompt live on the confirmation screen.
        assertFalse(
            shouldShowOfflineSyncBlocker(
                true,
                isConnected = false,
                sendResult = SendResult.Failed(SendErrorLowFee),
            )
        )
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
        assertTrue(
            isOfflineRetryInProgress(retrying = true, syncRetrying = false, isConnected = false, hasAdapterError = true)
        )
    }

    @Test
    fun isOfflineRetryInProgress_syncRetryingConnectedNoError_returnsTrue() {
        assertTrue(
            isOfflineRetryInProgress(retrying = false, syncRetrying = true, isConnected = true, hasAdapterError = false)
        )
    }

    @Test
    fun isOfflineRetryInProgress_syncRetryingButOffline_returnsFalse() {
        assertFalse(
            isOfflineRetryInProgress(
                retrying = false, syncRetrying = true, isConnected = false, hasAdapterError = false
            )
        )
    }

    @Test
    fun isOfflineRetryInProgress_idle_returnsFalse() {
        assertFalse(
            isOfflineRetryInProgress(
                retrying = false, syncRetrying = false, isConnected = true, hasAdapterError = false
            )
        )
    }

    // endregion
}

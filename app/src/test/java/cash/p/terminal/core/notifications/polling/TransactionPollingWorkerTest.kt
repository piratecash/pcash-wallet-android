package cash.p.terminal.core.notifications.polling

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.premium.settings.PollingInterval
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the worker companion contract — these are the entry points used
 * both by the coordinator (when realtime FGS fails to start) and by the
 * service's onTimeout handler. Service.onTimeout has no unit-level coverage
 * (Service requires Robolectric), so this suite is the line of defense for
 * the fallback transport.
 */
class TransactionPollingWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private var fallbackActive = false

    @Before
    fun setUp() {
        fallbackActive = false
        every { localStorage.pushRealtimeFallbackPollingActive } answers { fallbackActive }
        every { localStorage.pushRealtimeFallbackPollingActive = any() } answers {
            fallbackActive = firstArg<Boolean>()
        }
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun fallbackInterval_isFiveMinutes() {
        // Pinned to MIN_5 — drift here changes how often Android 15 wakes us
        // up after the FGS budget is exhausted.
        assertEquals(5L, TransactionPollingWorker.FALLBACK_INTERVAL_MINUTES)
        assertEquals(
            PollingInterval.MIN_5.minutes,
            TransactionPollingWorker.FALLBACK_INTERVAL_MINUTES,
        )
    }

    @Test
    fun start_enqueuesUniqueWorkWithReplacePolicyAndGivenInterval() {
        val requestSlot = slot<OneTimeWorkRequest>()

        val result = TransactionPollingWorker.start(context, intervalMinutes = 7L)

        assertTrue(result)
        verify {
            workManager.enqueueUniqueWork(
                any<String>(),
                ExistingWorkPolicy.REPLACE,
                capture(requestSlot),
            )
        }
        assertEquals(
            TimeUnit.MINUTES.toMillis(7L),
            requestSlot.captured.workSpec.initialDelay,
        )
    }

    @Test
    fun start_workManagerThrows_returnsFalseWithoutCrash() {
        every {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        } throws IllegalStateException("WorkManager unavailable")

        val result = TransactionPollingWorker.start(context, intervalMinutes = 5L)

        assertFalse(result)
    }

    @Test
    fun startFallback_setsFlagAndEnqueuesAtFallbackInterval() {
        val requestSlot = slot<OneTimeWorkRequest>()

        val result = TransactionPollingWorker.startFallback(context, localStorage)

        assertTrue(result)
        assertTrue(fallbackActive, "fallback flag must flip to true on success")
        verify {
            workManager.enqueueUniqueWork(
                any<String>(),
                ExistingWorkPolicy.REPLACE,
                capture(requestSlot),
            )
        }
        assertEquals(
            TimeUnit.MINUTES.toMillis(TransactionPollingWorker.FALLBACK_INTERVAL_MINUTES),
            requestSlot.captured.workSpec.initialDelay,
            "fallback chain must be scheduled at FALLBACK_INTERVAL_MINUTES",
        )
    }

    @Test
    fun startFallback_flagFlipsBeforeEnqueueObservedFromInside() {
        // Invariant: when the worker reads pushRealtimeFallbackPollingActive
        // during enqueue (any callback Wm fires synchronously), the flag must
        // already be true — otherwise the worker self-cancels on cycle 1.
        var flagSeenDuringEnqueue: Boolean? = null
        every {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        } answers {
            flagSeenDuringEnqueue = fallbackActive
            mockk(relaxed = true)
        }

        TransactionPollingWorker.startFallback(context, localStorage)

        assertEquals(true, flagSeenDuringEnqueue)
    }

    @Test
    fun startFallback_workManagerThrows_rollsBackFlagAndReturnsFalse() {
        every {
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        } throws IllegalStateException("WorkManager unavailable")

        val result = TransactionPollingWorker.startFallback(context, localStorage)

        assertFalse(result)
        assertFalse(fallbackActive, "flag must be rolled back on enqueue failure")
    }

    @Test
    fun cancel_workManagerThrows_returnsFalseWithoutCrash() {
        every {
            workManager.cancelUniqueWork(any())
        } throws IllegalStateException("WorkManager unavailable")

        val result = TransactionPollingWorker.cancel(context)

        assertFalse(result)
    }

    @Test
    fun cancel_returnsTrueOnSuccess() {
        val result = TransactionPollingWorker.cancel(context)

        assertTrue(result)
        verify { workManager.cancelUniqueWork(any()) }
    }
}

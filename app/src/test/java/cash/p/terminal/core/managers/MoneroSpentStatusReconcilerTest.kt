package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroSpentStatusReconcilerTest {

    @Test
    fun pendingRecovery_successfulCallbackCompletesOnlyAfterItIsArmed() = runTest {
        val reconciler = reconciler(this)
        val session = reconciler.activate()
        val recoveryGeneration = reconciler.beginRecovery(session)

        assertEquals(recoveryGeneration, reconciler.beginRequest(session))
        val generation = checkNotNull(recoveryGeneration)
        assertEquals(
            ReconciliationCallbackDisposition.Ignore,
            reconciler.callbackDisposition(session, generation, callbackIsSuccessful = true),
        )
        assertTrue(reconciler.hasActiveOperation(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))

        assertEquals(
            ReconciliationCallbackDisposition.Finalize,
            reconciler.callbackDisposition(session, generation, callbackIsSuccessful = true),
        )
        assertNull(reconciler.awaitingCallbackGeneration(session))
        assertTrue(reconciler.hasActiveOperation(session))

        reconciler.clearOperation(session)

        assertFalse(reconciler.hasActiveOperation(session))
    }

    @Test
    fun matchingFailedCallbackIsConsumedAndCanBeRetried() = runTest {
        val reconciler = reconciler(this)
        val session = reconciler.activate()
        val generation = checkNotNull(reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))

        assertEquals(
            ReconciliationCallbackDisposition.FailClosed,
            reconciler.callbackDisposition(session, generation, callbackIsSuccessful = false),
        )
        assertFalse(reconciler.hasActiveOperation(session))

        val retryGeneration = checkNotNull(reconciler.beginRecovery(session))
        assertTrue(retryGeneration > generation)
    }

    @Test
    fun staleSessionCannotMutateReplacementSession() = runTest {
        val reconciler = reconciler(this)
        val staleSession = reconciler.activate()
        val staleGeneration = checkNotNull(reconciler.beginRequest(staleSession))
        assertTrue(reconciler.markAwaitingCallback(staleSession, staleGeneration))

        val currentSession = reconciler.activate()

        assertFalse(reconciler.isActive(staleSession))
        assertTrue(reconciler.isActive(currentSession))
        assertNull(reconciler.beginRequest(staleSession))
        assertEquals(
            ReconciliationCallbackDisposition.Ignore,
            reconciler.callbackDisposition(
                staleSession,
                staleGeneration,
                callbackIsSuccessful = true,
            ),
        )
        assertFalse(reconciler.hasActiveOperation(currentSession))
    }

    @Test
    fun deactivationCancelsLaunchedReconciliation() = runTest {
        val reconciler = reconciler(this)
        val session = reconciler.activate()
        val generation = checkNotNull(reconciler.beginRecovery(session))
        val expectedPhase = MoneroReconciliationPhase.RecoveryRequested(generation)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        reconciler.launch(session, expectedPhase) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        runCurrent()
        started.await()

        reconciler.deactivate()
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertNull(reconciler.activeSession())
        assertFalse(reconciler.isInPhase(session, expectedPhase))
    }

    @Test
    fun clearedOperationDoesNotLaunchQueuedWork() = runTest {
        val reconciler = reconciler(this)
        val session = reconciler.activate()
        val generation = checkNotNull(reconciler.beginRecovery(session))
        val expectedPhase = MoneroReconciliationPhase.RecoveryRequested(generation)
        var launched = false

        reconciler.launch(session, expectedPhase) {
            launched = true
        }
        reconciler.clearOperation(session)
        runCurrent()

        assertFalse(launched)
    }

    @Test
    fun queuedRecoveryDoesNotLaunchAfterSameGenerationMovesToAwaitingCallback() = runTest {
        val reconciler = reconciler(this)
        val session = reconciler.activate()
        val generation = checkNotNull(reconciler.beginRecovery(session))
        val recoveryPhase = MoneroReconciliationPhase.RecoveryRequested(generation)
        var launched = false

        reconciler.launch(session, recoveryPhase) {
            launched = true
        }
        assertEquals(generation, reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))
        runCurrent()

        assertFalse(launched)
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
    }

    private fun reconciler(scope: TestScope): MoneroSpentStatusReconciler {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return MoneroSpentStatusReconciler(
            TestDispatcherProvider(dispatcher, scope),
        )
    }
}

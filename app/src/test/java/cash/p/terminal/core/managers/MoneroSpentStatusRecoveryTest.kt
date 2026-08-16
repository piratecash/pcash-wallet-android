package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroSpentStatusRecoveryTest {

    @Test
    fun pendingRecovery_rescanCallbackStoreResumeAndReadyRunInOrder() = runTest {
        val fixture = fixture()

        assertEquals(
            MoneroSpentStatusRequestResult.AwaitingCallback,
            fixture.recovery.request(fixture.session, FakeWallet),
        )
        assertEquals(
            fixture.generation,
            fixture.reconciler.awaitingCallbackGeneration(fixture.session),
        )
        assertEquals(
            ReconciliationCallbackDisposition.Finalize,
            fixture.recovery.acceptCallback(
                fixture.session,
                fixture.generation,
                callbackIsSuccessful = true,
            ),
        )

        fixture.recovery.finalizeAcceptedCallback(fixture.session, FakeWallet)

        assertEquals(fullRecoveryEvents, fixture.operations.events)
        assertTrue(fixture.operations.readyPersisted)
        assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
    }

    @Test
    fun resumeFailureAfterStoreLeavesDurableStatePendingAndAllowsRetry() = runTest {
        val fixture = fixture(failStoreResume = true)
        fixture.recovery.request(fixture.session, FakeWallet)
        fixture.recovery.acceptCallback(
            fixture.session,
            fixture.generation,
            callbackIsSuccessful = true,
        )

        val failure = runCatching {
            fixture.recovery.finalizeAcceptedCallback(fixture.session, FakeWallet)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(fixture.operations.readyPersisted)
        assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
        assertTrue(
            checkNotNull(fixture.reconciler.beginRecovery(fixture.session)) > fixture.generation,
        )
    }

    @Test
    fun rescanFailureIsFailClosedAndNeverPersistsReady() = runTest {
        val fixture = fixture(failRescan = true)

        val failure = runCatching {
            fixture.recovery.request(fixture.session, FakeWallet)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertNull(fixture.reconciler.awaitingCallbackGeneration(fixture.session))
        assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
        assertFalse(fixture.operations.readyPersisted)
    }

    @Test
    fun successfulCallbackDuringRescanResumeKeepsFinalizationActive() = runTest {
        val fixture = fixture()
        fixture.operations.onRescanResume = {
            assertEquals(
                ReconciliationCallbackDisposition.Finalize,
                fixture.recovery.acceptCallback(
                    fixture.session,
                    fixture.generation,
                    callbackIsSuccessful = true,
                ),
            )
        }

        assertEquals(
            MoneroSpentStatusRequestResult.AwaitingCallback,
            fixture.recovery.request(fixture.session, FakeWallet),
        )

        assertNull(fixture.reconciler.awaitingCallbackGeneration(fixture.session))
        assertTrue(
            fixture.reconciler.isInPhase(
                fixture.session,
                MoneroReconciliationPhase.Finalizing(fixture.generation),
            ),
        )
        fixture.recovery.finalizeAcceptedCallback(fixture.session, FakeWallet)
        assertTrue(fixture.operations.readyPersisted)
        assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
    }

    @Test
    fun unhealthyRetryClearsRecoveryOperation() = runTest {
        val fixture = fixture(fullyHealthy = false)

        assertEquals(
            MoneroSpentStatusRequestResult.Retry,
            fixture.recovery.request(fixture.session, FakeWallet),
        )

        assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
        assertFalse(fixture.operations.readyPersisted)
    }

    private fun TestScope.fixture(
        failRescan: Boolean = false,
        failStoreResume: Boolean = false,
        fullyHealthy: Boolean = true,
    ): RecoveryFixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reconciler = MoneroSpentStatusReconciler(
            TestDispatcherProvider(dispatcher, this),
        )
        val session = reconciler.activate()
        val generation = checkNotNull(reconciler.beginRecovery(session))
        val operations = FakeRecoveryOperations(
            reconciler = reconciler,
            session = session,
            failRescan = failRescan,
            failStoreResume = failStoreResume,
            fullyHealthy = fullyHealthy,
        )
        return RecoveryFixture(
            reconciler = reconciler,
            session = session,
            generation = generation,
            operations = operations,
            recovery = MoneroSpentStatusRecovery(reconciler, operations),
        )
    }

    private data class RecoveryFixture(
        val reconciler: MoneroSpentStatusReconciler,
        val session: Long,
        val generation: Long,
        val operations: FakeRecoveryOperations,
        val recovery: MoneroSpentStatusRecovery<FakeWallet>,
    )

    private data object FakeWallet

    private val fullRecoveryEvents = listOf(
        "healthy",
        "known-key-images",
        "pause-rescan",
        "healthy",
        "known-key-images",
        "armed-rescan",
        "resume-rescan",
        "healthy",
        "known-key-images",
        "pause-store",
        "store",
        "resume-store",
        "healthy",
        "known-key-images",
        "persist-ready",
    )

    private class FakeRecoveryOperations(
        private val reconciler: MoneroSpentStatusReconciler,
        private val session: Long,
        private val failRescan: Boolean = false,
        private val failStoreResume: Boolean = false,
        private val fullyHealthy: Boolean = true,
    ) : MoneroSpentStatusRecoveryOperations<FakeWallet> {
        val events = mutableListOf<String>()
        var readyPersisted = false
        var onRescanResume: (() -> Unit)? = null

        override fun isFullyHealthy(wallet: FakeWallet): Boolean {
            events += "healthy"
            return fullyHealthy
        }

        override fun hasUnknownKeyImages(wallet: FakeWallet): Boolean {
            events += "known-key-images"
            return false
        }

        override suspend fun performPreservingRescan(
            wallet: FakeWallet,
            request: MoneroSpentStatusRescanRequest,
        ) {
            events += "pause-rescan"
            request.armAndRequest()
            events += "resume-rescan"
            onRescanResume?.invoke()
        }

        override fun requestPreservingRescan(wallet: FakeWallet) {
            check(reconciler.awaitingCallbackGeneration(session) != null)
            events += "armed-rescan"
            check(!failRescan) { "rescan failed" }
        }

        override suspend fun storeReconciledWallet(wallet: FakeWallet) {
            events += "pause-store"
            events += "store"
            events += "resume-store"
            check(!failStoreResume) { "resume failed" }
        }

        override fun persistReady() {
            events += "persist-ready"
            readyPersisted = true
        }
    }
}

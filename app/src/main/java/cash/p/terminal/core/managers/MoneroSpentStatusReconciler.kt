package cash.p.terminal.core.managers

import io.horizontalsystems.core.DispatcherProvider
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

internal class MoneroSpentStatusReconciler(
    private val dispatcherProvider: DispatcherProvider,
) {
    private val nextSession = AtomicLong()
    private val state = AtomicReference(MoneroReconciliationState())

    fun activate(): Long {
        val session = nextSession.incrementAndGet()
        val scope = CoroutineScope(
            SupervisorJob() + dispatcherProvider.io + CoroutineExceptionHandler { _, error ->
                Timber.e(error, "Monero spent-status reconciliation failed")
            },
        )
        state.getAndSet(
            MoneroReconciliationState(
                session = session,
                scope = scope,
            ),
        ).scope?.cancel()
        return session
    }

    fun deactivate() {
        val previous = state.getAndSet(MoneroReconciliationState())
        previous.scope?.cancel()
    }

    fun activeSession(): Long? = state.get().session

    fun isActive(session: Long): Boolean = state.get().session == session

    fun hasActiveOperation(session: Long): Boolean {
        val current = state.get()
        return current.session == session && current.phase !is MoneroReconciliationPhase.Idle
    }

    fun awaitingCallbackGeneration(session: Long): Long? {
        val current = state.get()
        return if (current.session == session) {
            (current.phase as? MoneroReconciliationPhase.AwaitingCallback)?.generation
        } else {
            null
        }
    }

    fun beginRequest(session: Long): Long? =
        update { current ->
            if (current.session != session) return@update null
            val generation = when (val phase = current.phase) {
                MoneroReconciliationPhase.Idle -> current.generation + 1
                is MoneroReconciliationPhase.RecoveryRequested -> phase.generation
                else -> return@update null
            }
            current.copy(
                generation = generation,
                phase = MoneroReconciliationPhase.Requesting(generation),
            )
        }?.generation

    fun markAwaitingCallback(session: Long, generation: Long): Boolean =
        update { current ->
            if (
                current.session != session ||
                current.phase != MoneroReconciliationPhase.Requesting(generation)
            ) {
                null
            } else {
                current.copy(phase = MoneroReconciliationPhase.AwaitingCallback(generation))
            }
        } != null

    fun beginRecovery(session: Long): Long? =
        update { current ->
            if (
                current.session != session ||
                current.phase !is MoneroReconciliationPhase.Idle
            ) {
                null
            } else {
                val generation = current.generation + 1
                current.copy(
                    generation = generation,
                    phase = MoneroReconciliationPhase.RecoveryRequested(generation),
                )
            }
        }?.generation

    fun callbackDisposition(
        session: Long,
        generation: Long,
        callbackIsSuccessful: Boolean,
    ): ReconciliationCallbackDisposition {
        while (true) {
            val current = state.get()
            if (current.session != session) return ReconciliationCallbackDisposition.Ignore
            val disposition = reconciliationCallbackDisposition(
                awaitingGeneration =
                    (current.phase as? MoneroReconciliationPhase.AwaitingCallback)?.generation,
                callbackGeneration = generation,
                callbackIsSuccessful = callbackIsSuccessful,
            )
            val nextPhase = when (disposition) {
                ReconciliationCallbackDisposition.FailClosed -> MoneroReconciliationPhase.Idle
                ReconciliationCallbackDisposition.Finalize ->
                    MoneroReconciliationPhase.Finalizing(generation)
                ReconciliationCallbackDisposition.Ignore -> return disposition
            }
            if (state.compareAndSet(current, current.copy(phase = nextPhase))) {
                return disposition
            }
        }
    }

    fun clearOperation(session: Long) {
        update { current ->
            current.takeIf { it.session == session }
                ?.copy(phase = MoneroReconciliationPhase.Idle)
        }
    }

    fun consumeFailedAwaitingCallback(session: Long): Boolean =
        update { current ->
            if (
                current.session == session &&
                current.phase is MoneroReconciliationPhase.AwaitingCallback
            ) {
                current.copy(phase = MoneroReconciliationPhase.Idle)
            } else {
                null
            }
        } != null

    fun launch(
        session: Long,
        expectedPhase: MoneroReconciliationPhase,
        block: suspend () -> Unit,
    ) {
        val current = state.get()
        val scope = current.scope ?: return
        if (
            current.session != session ||
            current.phase != expectedPhase ||
            scope.coroutineContext[Job]?.isActive != true
        ) {
            return
        }
        scope.launch {
            if (isInPhase(session, expectedPhase)) {
                block()
            }
        }
    }

    fun isInPhase(session: Long, expectedPhase: MoneroReconciliationPhase): Boolean {
        val current = state.get()
        return current.session == session && current.phase == expectedPhase
    }

    private fun update(
        transform: (MoneroReconciliationState) -> MoneroReconciliationState?,
    ): MoneroReconciliationState? {
        while (true) {
            val current = state.get()
            val updated = transform(current) ?: return null
            if (state.compareAndSet(current, updated)) return updated
        }
    }
}

private data class MoneroReconciliationState(
    val session: Long? = null,
    val generation: Long = 0,
    val phase: MoneroReconciliationPhase = MoneroReconciliationPhase.Idle,
    val scope: CoroutineScope? = null,
)

internal sealed class MoneroReconciliationPhase {
    abstract val generation: Long?

    data object Idle : MoneroReconciliationPhase() {
        override val generation: Long? = null
    }

    data class RecoveryRequested(override val generation: Long) : MoneroReconciliationPhase()
    data class Requesting(override val generation: Long) : MoneroReconciliationPhase()
    data class AwaitingCallback(override val generation: Long) : MoneroReconciliationPhase()
    data class Finalizing(override val generation: Long) : MoneroReconciliationPhase()
}

internal enum class ReconciliationCallbackDisposition {
    Ignore,
    FailClosed,
    Finalize,
}

internal fun isMatchingReconciliationCallback(
    awaitingGeneration: Long?,
    callbackGeneration: Long,
): Boolean = awaitingGeneration == callbackGeneration

internal fun reconciliationCallbackDisposition(
    awaitingGeneration: Long?,
    callbackGeneration: Long,
    callbackIsSuccessful: Boolean,
): ReconciliationCallbackDisposition =
    if (!isMatchingReconciliationCallback(awaitingGeneration, callbackGeneration)) {
        ReconciliationCallbackDisposition.Ignore
    } else if (!callbackIsSuccessful) {
        ReconciliationCallbackDisposition.FailClosed
    } else {
        ReconciliationCallbackDisposition.Finalize
    }

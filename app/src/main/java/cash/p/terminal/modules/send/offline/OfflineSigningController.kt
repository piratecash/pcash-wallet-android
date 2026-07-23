package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineSigningController<T>(
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val payloadEncoder: OfflineTransactionPayloadEncoder,
    private val repository: OfflineSignedTransactionRepository,
    private val cautionFactory: (Throwable) -> HSCaution,
    private val isSilentCancellation: (Throwable) -> Boolean,
) {
    var signState by mutableStateOf<OfflineSignState>(OfflineSignState.Idle)
        private set

    var signedTransaction by mutableStateOf<OfflineSignedTransaction?>(null)
        private set

    private var job: Job? = null

    fun sign(
        format: OfflineTransactionFormat,
        producer: suspend () -> T,
        draftBuilder: (T) -> OfflineSignedTransactionDraft,
    ) {
        if (signState is OfflineSignState.Signing) return

        signState = OfflineSignState.Signing
        job = scope.launch {
            signInternal(format, producer, draftBuilder)
        }
    }

    fun resetSignState() {
        job?.cancel()
        job = null
        signState = OfflineSignState.Idle
    }

    fun closeTransfer() {
        signedTransaction = null
        signState = OfflineSignState.Idle
    }

    private suspend fun signInternal(
        format: OfflineTransactionFormat,
        producer: suspend () -> T,
        draftBuilder: (T) -> OfflineSignedTransactionDraft,
    ) {
        try {
            signedTransaction = null
            withContext(dispatcherProvider.io) {
                val result = producer()
                val signingJob = coroutineContext[Job]
                // A producer returning normally means the signature must be preserved —
                // for TON the one-shot seqno is already consumed by now. Finish encoding
                // and persisting non-cancellably so a cancellation landing after signing
                // (the UI still shows "signing…" until the save completes) cannot burn
                // the seqno while dropping the transaction.
                withContext(NonCancellable) {
                    val draft = draftBuilder(result)
                    val payload = payloadEncoder.encode(draft)
                    val transaction = OfflineSignedTransaction(
                        rawHex = draft.rawHex,
                        pcashPayload = payload,
                        txHash = draft.txHash,
                        createdAt = draft.createdAt,
                    )
                    repository.save(draft, payload)
                    // Persist unconditionally, but surface the result only while this
                    // attempt is still current: once the user has reset (which cancels
                    // this job), publishing Signed would resurrect an abandoned transfer
                    // and auto-navigate into it on the next entry.
                    if (signingJob?.isCancelled != true) {
                        signedTransaction = transaction
                        signState = OfflineSignState.Signed(format.preferredTransferFormat(transaction))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            signState = if (isSilentCancellation(e)) {
                OfflineSignState.Idle
            } else {
                OfflineSignState.Failed(cautionFactory(e))
            }
        }
    }
}

sealed interface OfflineSignState {
    data object Idle : OfflineSignState
    data object Signing : OfflineSignState
    data class Signed(val format: OfflineTransactionFormat) : OfflineSignState
    data class Failed(val caution: HSCaution) : OfflineSignState
}

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
    private data class SignedDraft(
        val draft: OfflineSignedTransactionDraft,
        val payload: String,
        val transaction: OfflineSignedTransaction,
        val transferFormat: OfflineTransactionFormat,
    )

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
            val signed = withContext(dispatcherProvider.io) {
                val draft = draftBuilder(producer())
                val payload = payloadEncoder.encode(draft)
                val transaction = OfflineSignedTransaction(
                    rawHex = draft.rawHex,
                    pcashPayload = payload,
                    txHash = draft.txHash,
                    createdAt = draft.createdAt,
                )
                SignedDraft(
                    draft = draft,
                    payload = payload,
                    transaction = transaction,
                    transferFormat = format.preferredTransferFormat(transaction),
                )
            }
            signedTransaction = signed.transaction
            repository.save(signed.draft, signed.payload)
            signState = OfflineSignState.Signed(signed.transferFormat)
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

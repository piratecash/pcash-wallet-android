package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashRawBroadcastResultTest {

    @Test
    fun toZcashRawBroadcastResult_success_returnsSubmitted() {
        val result = TransactionSubmitResult.Success(txId).toZcashRawBroadcastResult()

        assertEquals(expectedTxHash, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.Submitted, result.status)
    }

    @Test
    fun toZcashRawBroadcastResult_alreadyCommittedFailure_returnsAlreadyKnown() {
        val result = TransactionSubmitResult.Failure(
            txId = txId,
            grpcError = false,
            code = -1,
            description = "any transaction with the same effects will be rejected from the mempool " +
                    "until a chain reset: transaction was committed to the best chain"
        ).toZcashRawBroadcastResult()

        assertEquals(expectedTxHash, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.AlreadyKnown, result.status)
    }

    private companion object {
        val txId = FirstClassByteArray(ByteArray(32) { it.toByte() })
        val expectedTxHash = (31 downTo 0).joinToString(separator = "") { "%02x".format(it) }
    }
}

package cash.p.terminal.modules.multiswap.sendtransaction

import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SendTransactionResultTest {

    @Test
    fun getRecordUid_btcResult_returnsUidNotCanonicalHash() {
        val result = SendTransactionResult.Btc(uid = "u", canonicalHashReversedHex = "h")

        assertEquals("u", result.getRecordUid())
    }

    @Test
    fun getCanonicalTxHash_btcResult_returnsCanonicalHashNotUid() {
        val result = SendTransactionResult.Btc(uid = "u", canonicalHashReversedHex = "h")

        assertEquals("h", result.getCanonicalTxHash())
        assertNotEquals(result.getRecordUid(), result.getCanonicalTxHash())
    }

    @Test
    fun getCanonicalTxHash_evmResult_equalsRecordUid() {
        val mockedTransaction = mockk<Transaction>(relaxed = true) {
            every { hashString } returns "0xabc"
        }
        val fullTransaction = mockk<FullTransaction>(relaxed = true) {
            every { transaction } returns mockedTransaction
        }
        val result = SendTransactionResult.Evm(fullTransaction)

        assertEquals(result.getRecordUid(), result.getCanonicalTxHash())
        assertEquals("0xabc", result.getCanonicalTxHash())
    }
}

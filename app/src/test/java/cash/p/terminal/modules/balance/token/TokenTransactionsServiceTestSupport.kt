package cash.p.terminal.modules.balance.token

import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.every
import io.mockk.mockk

/**
 * Shared helpers for [TokenTransactionsService] unit tests: a relaxed [TransactionRecord] mock
 * and a polling wait, reused by [TokenTransactionsServiceServiceVersionTest] and
 * [TokenTransactionsServiceSearchTest] to avoid duplicating the same setup in both files.
 */
internal fun mockRecord(uid: String, source: TransactionSource) =
    mockk<TransactionRecord>(relaxed = true) {
        every { this@mockk.uid } returns uid
        every { this@mockk.source } returns source
        every { mainValue } returns null
    }

internal fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10)
    }
}

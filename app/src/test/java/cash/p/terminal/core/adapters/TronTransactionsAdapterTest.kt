package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.TransactionTag
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TronTransactionsAdapterTest {

    private val tronKit = mockk<TronKit>()
    private val assetId = "1005114"
    private val incomingTag = TransactionTag.trc10Incoming(assetId)
    private val outgoingTag = TransactionTag.trc10Outgoing(assetId)
    private val allTrc10Tags = listOf(incomingTag, outgoingTag)
    private val adapter = TronTransactionsAdapter(
        tronKitWrapper = mockk<TronKitWrapper> {
            every { this@mockk.tronKit } returns this@TronTransactionsAdapterTest.tronKit
        },
        transactionConverter = mockk(relaxed = true),
    )
    private val token = Token(
        coin = Coin(uid = "trc10-$assetId", name = "TRC10", code = "TRC10"),
        blockchain = Blockchain(BlockchainType.Tron, "TRON", null),
        type = TokenType.Trc10(assetId),
        decimals = 0,
    )

    @Test
    fun getTransactions_trc10All_usesIncomingOrOutgoingAssetTags() = runTest {
        val filters = captureFilters(FilterTransactionType.All)

        assertEquals(
            listOf(allTrc10Tags),
            filters,
        )
    }

    @Test
    fun getTransactions_trc10Incoming_addsIncomingDirectionTag() = runTest {
        val filters = captureFilters(FilterTransactionType.Incoming)

        assertEquals(
            listOf(
                allTrc10Tags,
                listOf(incomingTag),
            ),
            filters,
        )
    }

    @Test
    fun getTransactions_trc10Outgoing_addsOutgoingDirectionTag() = runTest {
        val filters = captureFilters(FilterTransactionType.Outgoing)

        assertEquals(
            listOf(
                allTrc10Tags,
                listOf(outgoingTag),
            ),
            filters,
        )
    }

    private suspend fun captureFilters(
        transactionType: FilterTransactionType,
    ): List<List<String>> {
        var capturedFilters = emptyList<List<String>>()
        coEvery {
            tronKit.getFullTransactionsBefore(any(), any(), any())
        } answers {
            capturedFilters = firstArg()
            emptyList()
        }

        adapter.getTransactions(
            from = null,
            token = token,
            limit = 20,
            transactionType = transactionType,
            address = null,
        )

        return capturedFilters
    }
}

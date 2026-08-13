package cash.p.terminal.core.notifications.polling

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.MoneroKitWrapper
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroTransactionsPollerTest {

    private val kitManager = mockk<MoneroKitManager>(relaxed = true)
    private val transactionAdapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val poller = MoneroTransactionsPoller(kitManager, transactionAdapterManager)

    private fun mockWallet() = mockk<Wallet>(relaxed = true) {
        every { token.blockchainType } returns BlockchainType.Monero
    }

    private fun mockSyncedWrapper(): MoneroKitWrapper {
        return mockk(relaxed = true) {
            every { syncState } returns MutableStateFlow(AdapterState.Synced)
        }
    }

    @Test
    fun pollOnce_nullWrapper_returnsEmpty() = runTest {
        coEvery { kitManager.withPollingSession<List<TransactionRecord>>(any()) } returns null

        val result = poller.pollOnce(listOf(mockWallet()))

        assertTrue(result.isEmpty())
    }

    @Test
    fun pollOnce_synced_returnsTransactions() = runTest {
        val wallet = mockWallet()
        val record = mockk<TransactionRecord>()
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        coEvery { adapter.getTransactions(null, null, 100, any(), null) } returns listOf(record)
        every { transactionAdapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(wallet.transactionSource to adapter))
        val wrapper = mockSyncedWrapper()
        coEvery { kitManager.withPollingSession<List<TransactionRecord>>(any()) } coAnswers {
            firstArg<suspend (MoneroKitWrapper) -> List<TransactionRecord>>().invoke(wrapper)
        }

        val result = poller.pollOnce(listOf(wallet))

        assertEquals(listOf(record), result)
    }

    @Test
    fun pollOnce_timeout_returnsEmpty() = runTest {
        coEvery { kitManager.withPollingSession<List<TransactionRecord>>(any()) } coAnswers {
            delay(60_001)
            null
        }

        val result = poller.pollOnce(listOf(mockWallet()))

        assertTrue(result.isEmpty())
    }

}

package cash.p.terminal.core.usecase

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveTransactionItemUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(dispatcher)
    private val transactionAdapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val useCase = ResolveTransactionItemUseCase(
        transactionAdapterManager = transactionAdapterManager,
        swapProviderTransactionsStorage = storage,
        dispatcherProvider = TestDispatcherProvider(dispatcher, testScope),
    )

    private fun adapterWithRecord(uid: String): ITransactionsAdapter {
        val record = mockk<TransactionRecord>(relaxed = true) {
            every { this@mockk.uid } returns uid
        }
        return mockk<ITransactionsAdapter>(relaxed = true).also {
            coEvery { it.getTransactions(any(), any(), any(), any(), any()) } returns listOf(record)
        }
    }

    private fun emptyAdapter(): ITransactionsAdapter = mockk<ITransactionsAdapter>(relaxed = true).also {
        coEvery { it.getTransactions(any(), any(), any(), any(), any()) } returns emptyList()
    }

    private fun swapProviderTx(
        outgoingRecordUid: String? = "outgoing-uid",
        incomingRecordUid: String? = null,
        provider: SwapProvider = SwapProvider.CHANGENOW,
        transactionId: String = "cn-tx-123",
    ) = SwapProviderTransaction(
        date = 1000L,
        outgoingRecordUid = outgoingRecordUid,
        transactionId = transactionId,
        status = "finished",
        provider = provider,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal.ONE,
        addressIn = "0xSender",
        coinUidOut = "tether",
        blockchainTypeOut = "ethereum",
        amountOut = BigDecimal("100"),
        addressOut = "0xRecipient",
        incomingRecordUid = incomingRecordUid,
        accountId = "account-1",
    )

    @Test
    fun invoke_recordInAdapter_returnsItem() = runTest(dispatcher) {
        val source = mockk<TransactionSource>(relaxed = true)
        every { transactionAdapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to adapterWithRecord("tx-uid")))
        every { storage.getByOutgoingRecordUid("tx-uid") } returns null
        every { storage.getByIncomingRecordUid("tx-uid") } returns null

        val result = useCase("tx-uid")

        assertNotNull(result)
        assertEquals("tx-uid", result?.record?.uid)
        assertNull(result?.changeNowTransactionId)
        assertNull(result?.transactionStatusUrl)
    }

    @Test
    fun invoke_recordWithOutgoingSwap_enrichesItem() = runTest(dispatcher) {
        val source = mockk<TransactionSource>(relaxed = true)
        every { transactionAdapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to adapterWithRecord("outgoing-uid")))
        every { storage.getByOutgoingRecordUid("outgoing-uid") } returns swapProviderTx()

        val result = useCase("outgoing-uid")

        assertEquals("cn-tx-123", result?.changeNowTransactionId)
        assertEquals("changenow.io", result?.transactionStatusUrl?.first)
    }

    @Test
    fun invoke_recordWithIncomingSwap_enrichesItem() = runTest(dispatcher) {
        val source = mockk<TransactionSource>(relaxed = true)
        every { transactionAdapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to adapterWithRecord("incoming-uid")))
        every { storage.getByOutgoingRecordUid("incoming-uid") } returns null
        every { storage.getByIncomingRecordUid("incoming-uid") } returns swapProviderTx(
            outgoingRecordUid = null,
            incomingRecordUid = "incoming-uid",
            provider = SwapProvider.QUICKEX,
            transactionId = "qx-456",
        )

        val result = useCase("incoming-uid")

        assertEquals("qx-456", result?.changeNowTransactionId)
        assertEquals("quickex.io", result?.transactionStatusUrl?.first)
    }

    @Test
    fun invoke_recordAppearsAfterAdaptersUpdate_returnsItem() = runTest(dispatcher) {
        val source = mockk<TransactionSource>(relaxed = true)
        val flow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { transactionAdapterManager.adaptersReadyFlow } returns flow
        every { storage.getByOutgoingRecordUid(any()) } returns null
        every { storage.getByIncomingRecordUid(any()) } returns null

        flow.value = mapOf(source to adapterWithRecord("delayed-uid"))
        val result = useCase("delayed-uid", timeoutMs = 1_000)

        assertEquals("delayed-uid", result?.record?.uid)
    }

    @Test
    fun invoke_neverAppears_returnsNullAfterTimeout() = runTest(dispatcher) {
        val source = mockk<TransactionSource>(relaxed = true)
        every { transactionAdapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to emptyAdapter()))

        val result = useCase("missing-uid", timeoutMs = 50)

        assertNull(result)
    }
}

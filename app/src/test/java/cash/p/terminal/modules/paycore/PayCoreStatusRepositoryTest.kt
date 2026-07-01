package cash.p.terminal.modules.paycore

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PayCoreStatusRepositoryTest {

    private val apiService = mockk<PayCoreApiService>()
    private val storage = mockk<SwapProviderTransactionsStorage>()

    private lateinit var repository: PayCoreStatusRepository

    @Before
    fun setUp() {
        repository = PayCoreStatusRepository(apiService, storage)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getTransactionStatus_payout_pollsWithStoredId() = runTest {
        val payoutId = "payout-id-456"
        val transaction = createTransaction(
            transactionId = payoutId,
            outgoingRecordUid = "0xabc123",
            coinUidIn = "tether",
            blockchainTypeIn = "ethereum"
        )

        coEvery { storage.getTransaction(payoutId) } returns transaction
        coEvery { apiService.getTransactionStatus(payoutId, PayCoreTicker.USDT_ERC20) } returns statusResponse("Completed")

        val result = repository.getTransactionStatus(payoutId, "")

        coVerify { apiService.getTransactionStatus(payoutId, PayCoreTicker.USDT_ERC20) }
        assertEquals(TransactionStatusEnum.FINISHED, result.status)
    }

    @Test
    fun getTransactionStatus_payment_usesPaymentNetwork() = runTest {
        val transactionId = "payment-123"
        val transaction = createTransaction(
            transactionId = transactionId,
            coinUidIn = PayCoreAssets.RUB_COIN_UID,
            blockchainTypeOut = "tron"
        )

        coEvery { storage.getTransaction(transactionId) } returns transaction
        coEvery { apiService.getTransactionStatus(transactionId, PayCoreTicker.USDT) } returns statusResponse("Pending")

        repository.getTransactionStatus(transactionId, "")

        coVerify { apiService.getTransactionStatus(transactionId, PayCoreTicker.USDT) }
    }

    @Test
    fun getTransactionStatus_missingLocalTransaction_returnsWaiting() = runTest {
        val transactionId = "missing"
        coEvery { storage.getTransaction(transactionId) } returns null

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.WAITING, result.status)
        coVerify(exactly = 0) { apiService.getTransactionStatus(any(), any()) }
    }

    @Test
    fun getTransactionStatus_calculated_returnsNew() = runTest {
        assertStatus("Calculated", TransactionStatusEnum.NEW)
    }

    @Test
    fun getTransactionStatus_pending_returnsWaiting() = runTest {
        assertStatus("Pending", TransactionStatusEnum.WAITING)
    }

    @Test
    fun getTransactionStatus_paid_returnsExchanging() = runTest {
        assertStatus("Paid", TransactionStatusEnum.EXCHANGING)
    }

    @Test
    fun getTransactionStatus_exchanged_returnsSending() = runTest {
        assertStatus("Exchanged", TransactionStatusEnum.SENDING)
    }

    @Test
    fun getTransactionStatus_completed_returnsFinished() = runTest {
        assertStatus("Completed", TransactionStatusEnum.FINISHED)
    }

    @Test
    fun getTransactionStatus_expired_returnsFailed() = runTest {
        assertStatus("Expired", TransactionStatusEnum.FAILED)
    }

    @Test
    fun getTransactionStatus_unknownStatus_returnsFailed() = runTest {
        assertStatus("SomethingNew", TransactionStatusEnum.FAILED)
    }

    @Test
    fun getTransactionStatus_nullStatus_returnsWaiting() = runTest {
        assertStatus(null, TransactionStatusEnum.WAITING)
    }

    private suspend fun assertStatus(
        payCoreStatus: String?,
        expectedStatus: TransactionStatusEnum,
    ) {
        val transactionId = "tx-${payCoreStatus ?: "missing"}"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus(transactionId, PayCoreTicker.USDT_ERC20) } returns statusResponse(payCoreStatus)

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(expectedStatus, result.status)
    }

    private fun statusResponse(status: String?) = PayCoreTransactionStatusResponse(
        transactionStatus = status
    )

    private fun createTransaction(
        transactionId: String,
        outgoingRecordUid: String? = null,
        coinUidIn: String = "tether",
        blockchainTypeIn: String = "ethereum",
        blockchainTypeOut: String = "fiat",
    ) = SwapProviderTransaction(
        date = 1000L,
        outgoingRecordUid = outgoingRecordUid,
        transactionId = transactionId,
        status = "new",
        provider = SwapProvider.PAYCORE,
        coinUidIn = coinUidIn,
        blockchainTypeIn = blockchainTypeIn,
        amountIn = BigDecimal.ONE,
        addressIn = "0x1",
        coinUidOut = "rub",
        blockchainTypeOut = blockchainTypeOut,
        amountOut = BigDecimal.TEN,
        addressOut = "addr",
        accountId = "test-account",
    )
}

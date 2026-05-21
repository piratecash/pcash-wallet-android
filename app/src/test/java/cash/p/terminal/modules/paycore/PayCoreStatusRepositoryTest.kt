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
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)

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
        coEvery { apiService.getTransactionStatus("payout", payoutId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "SUCCESS",
            fiatTransactionStatus = "OK"
        )

        val result = repository.getTransactionStatus(payoutId, "")

        coVerify { apiService.getTransactionStatus("payout", payoutId, "ERC20") }
        assertEquals(TransactionStatusEnum.FINISHED, result.status)
    }

    @Test
    fun getTransactionStatus_unknownCryptoStatus_returnsFailed() = runTest {
        val transactionId = "tx-unknown"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "SOMETHING_NEW",
            fiatTransactionStatus = "OK"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.FAILED, result.status)
    }

    @Test
    fun getTransactionStatus_payment_usesPaymentType() = runTest {
        val transactionId = "payment-123"
        val transaction = createTransaction(
            transactionId = transactionId,
            coinUidIn = PayCoreAssets.RUB_COIN_UID,
            blockchainTypeOut = "tron"
        )

        coEvery { storage.getTransaction(transactionId) } returns transaction
        coEvery { apiService.getTransactionStatus("payment", transactionId, "TRC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "SUCCESS",
            fiatTransactionStatus = "OK"
        )

        repository.getTransactionStatus(transactionId, "")

        coVerify { apiService.getTransactionStatus("payment", transactionId, "TRC20") }
        coVerify(exactly = 0) { apiService.getTransactionStatus("payout", any(), any()) }
    }

    @Test
    fun getTransactionStatus_cryptoSuccessFiatOk_returnsFinished() = runTest {
        val transactionId = "tx-1"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "SUCCESS",
            fiatTransactionStatus = "OK"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.FINISHED, result.status)
    }

    @Test
    fun getTransactionStatus_cryptoFailedFiatOk_returnsFailed() = runTest {
        val transactionId = "tx-2"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "FAILED",
            fiatTransactionStatus = "OK"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.FAILED, result.status)
    }

    @Test
    fun getTransactionStatus_cryptoProcessingFiatOk_returnsWaiting() = runTest {
        val transactionId = "tx-3"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "PROCESSING",
            fiatTransactionStatus = "OK"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.WAITING, result.status)
    }

    @Test
    fun getTransactionStatus_payoutFailStatus_returnsFailed() = runTest {
        val transactionId = "tx-fail"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "FAIL",
            fiatTransactionStatus = null
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.FAILED, result.status)
    }

    @Test
    fun getTransactionStatus_payoutCancelByUserStatus_returnsFailed() = runTest {
        val transactionId = "tx-cancel"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "CANCEL_BY_USER",
            fiatTransactionStatus = null
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.FAILED, result.status)
    }

    @Test
    fun getTransactionStatus_fiatTimeoutStatus_returnsWaiting() = runTest {
        val transactionId = "tx-timeout"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = null,
            fiatTransactionStatus = "TIMEOUT"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.WAITING, result.status)
    }

    @Test
    fun getTransactionStatus_fiatCreatedOrWaitUser_returnsCreatedOrWaitUser() = runTest {
        val transactionId = "tx-wait-user"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = null,
            fiatTransactionStatus = "CREATED_OR_WAIT_USER"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.CREATED_OR_WAIT_USER, result.status)
    }

    @Test
    fun getTransactionStatus_cryptoSuccessFiatCreatedOrWaitUser_returnsCreatedOrWaitUser() = runTest {
        val transactionId = "tx-success-wait"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "SUCCESS",
            fiatTransactionStatus = "CREATED_OR_WAIT_USER"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.CREATED_OR_WAIT_USER, result.status)
    }

    @Test
    fun getTransactionStatus_cryptoCreatedOrWaitUserFiatFailed_returnsCreatedOrWaitUser() = runTest {
        val transactionId = "tx-wait-fail"
        coEvery { storage.getTransaction(transactionId) } returns createTransaction(transactionId = transactionId)
        coEvery { apiService.getTransactionStatus("payout", transactionId, "ERC20") } returns PayCoreTransactionStatusResponse(
            cryptoTransactionStatus = "CREATED_OR_WAIT_USER",
            fiatTransactionStatus = "CANCELED"
        )

        val result = repository.getTransactionStatus(transactionId, "")

        assertEquals(TransactionStatusEnum.CREATED_OR_WAIT_USER, result.status)
    }

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

package cash.p.terminal.core.managers

import cash.p.terminal.entities.PendingTransactionEntity
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingBalanceCalculatorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var pendingFlow: MutableStateFlow<List<PendingTransactionEntity>>

    @Before
    fun setUp() {
        pendingRepository = mockk(relaxed = true)
        pendingFlow = MutableStateFlow(emptyList())

        every { pendingRepository.getActivePendingFlow(any()) } returns pendingFlow

        dispatcherProvider = object : DispatcherProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
            override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
        }
    }

    @Test
    fun pendingChangedFlow_emitsWhenPendingTransactionAdded() = runTest(dispatcher) {
        // Given: PendingBalanceCalculator is observing an account
        val calculator = PendingBalanceCalculator(pendingRepository, dispatcherProvider)
        calculator.startObserving("account-1")
        advanceUntilIdle()

        var emitted = false
        val job = launch {
            calculator.pendingChangedFlow.first()
            emitted = true
        }

        // When: A pending transaction is added
        pendingFlow.value = listOf(createPendingEntity("tx-1"))
        advanceUntilIdle()

        // Then: pendingChangedFlow should emit
        job.cancel()
        assertTrue("pendingChangedFlow should emit when pending transaction is added", emitted)
    }

    @Test
    fun pendingChangedFlow_emitsWhenPendingTransactionRemoved() = runTest(dispatcher) {
        // Given: PendingBalanceCalculator has existing pending transactions
        pendingFlow.value = listOf(createPendingEntity("tx-1"))
        val calculator = PendingBalanceCalculator(pendingRepository, dispatcherProvider)
        calculator.startObserving("account-1")
        advanceUntilIdle()

        var emitted = false
        val job = launch {
            calculator.pendingChangedFlow.first()
            emitted = true
        }

        // When: Pending transaction is removed (confirmed)
        pendingFlow.value = emptyList()
        advanceUntilIdle()

        // Then: pendingChangedFlow should emit
        job.cancel()
        assertTrue("pendingChangedFlow should emit when pending transaction is removed", emitted)
    }

    private fun createPendingEntity(id: String) = PendingTransactionEntity(
        id = id,
        walletId = "account-1",
        coinUid = "toncoin",
        blockchainTypeUid = "ton",
        tokenTypeId = "native",
        meta = null,
        amountAtomic = "80000000000",
        feeAtomic = "100000000",
        sdkBalanceAtCreationAtomic = "87365700000",
        fromAddress = "",
        toAddress = "EQC...",
        txHash = null,
        nonce = null,
        memo = null,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 3600000
    )
}

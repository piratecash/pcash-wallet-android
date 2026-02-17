package cash.p.terminal.core.managers

import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

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

    @Test
    fun onPendingInserted_updatesCache_adjustBalanceReflectsDeduction() = runTest(dispatcher) {
        val calculator = PendingBalanceCalculator(pendingRepository, dispatcherProvider)
        val entity = createPendingEntity("tx-sync", blockchainTypeUid = "the-open-network")

        // When: entity inserted synchronously (no Room Flow)
        calculator.onPendingInserted(entity)

        // Then: adjustBalance should reflect the deduction immediately
        val wallet = createMockWallet()
        // sdkBalance = 87.3657 TON (same as sdkBalanceAtCreation → SDK hasn't deducted yet)
        val rawBalance = BalanceData(available = BigDecimal("87.3657"))
        val adjusted = calculator.adjustBalance(wallet, rawBalance)

        // Expected deduction: amount(80) + fee(0.1) = 80.1 TON
        val expectedBalance = BigDecimal("87.3657") - BigDecimal("80.1")
        assertEquals(
            expectedBalance.stripTrailingZeros(),
            adjusted.available.stripTrailingZeros()
        )
    }

    @Test
    fun onPendingInserted_emitsPendingChangedFlow() = runTest(dispatcher) {
        val calculator = PendingBalanceCalculator(pendingRepository, dispatcherProvider)

        var emitted = false
        val job = launch {
            calculator.pendingChangedFlow.first()
            emitted = true
        }

        // When: entity inserted synchronously
        calculator.onPendingInserted(createPendingEntity("tx-emit"))
        advanceUntilIdle()

        // Then: pendingChangedFlow should emit
        job.cancel()
        assertTrue("pendingChangedFlow should emit on onPendingInserted", emitted)
    }

    private fun createMockWallet(): Wallet {
        val account = mockk<Account> {
            every { id } returns "account-1"
        }
        val token = mockk<Token> {
            every { coin } returns Coin(uid = "toncoin", name = "Toncoin", code = "TON")
            every { type } returns TokenType.Native
            every { blockchainType } returns BlockchainType.Ton
            every { decimals } returns 9
        }
        return mockk {
            every { this@mockk.account } returns account
            every { this@mockk.token } returns token
        }
    }

    private fun createPendingEntity(
        id: String,
        blockchainTypeUid: String = "ton"
    ) = PendingTransactionEntity(
        id = id,
        walletId = "account-1",
        coinUid = "toncoin",
        blockchainTypeUid = blockchainTypeUid,
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

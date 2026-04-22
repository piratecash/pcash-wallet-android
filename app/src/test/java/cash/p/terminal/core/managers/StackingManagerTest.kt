package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.network.pirate.domain.enity.InvestmentData
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class StackingManagerTest {

    private val repository = mockk<PiratePlaceRepository>()
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val wallet = mockk<Wallet>()

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private lateinit var manager: StackingManager

    companion object {
        private const val ADDRESS = "PtestAddress123"
        private const val COIN = "pirate"
        private val BALANCE = BigDecimal("1000")
        private val NEXT_ACCRUAL = Instant.parse("2026-04-20T00:00:00Z")
        private val UNPAID = BigDecimal("42.5")
    }

    @Before
    fun setup() {
        mockkStatic("cash.p.terminal.wallet.ExtensionsKt")
        every { wallet.isPirateCash() } returns true
        every { wallet.isCosanta() } returns false
        every { localStorage.getStackingUnpaid(any(), any()) } returns null
        every { localStorage.getStackingNextAccrualAt(any(), any()) } returns null
        every { localStorage.getStackingCachedBalance(any(), any()) } returns null
        every { localStorage.getStackingTimestamp(any(), any()) } returns 0L

        manager = StackingManager(repository, localStorage, dispatcherProvider)
    }

    @Test
    fun coldStart_noCache_flowsStayNull() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFlow.value)
        assertNull(manager.nextAccrualAtFlow.value)
    }

    @Test
    fun coldStart_hasCache_emitsCachedValues() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)

        assertEquals(UNPAID, manager.unpaidFlow.value)
        assertEquals(NEXT_ACCRUAL, manager.nextAccrualAtFlow.value)
    }

    @Test
    fun apiSuccess_updatesFlowsAndSavesCache() = testScope.runTest {
        val data = investmentData(unrealizedValue = "42.5", nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(UNPAID, manager.unpaidFlow.value)
        assertEquals(NEXT_ACCRUAL, manager.nextAccrualAtFlow.value)
        verify {
            localStorage.saveStackingData(COIN, ADDRESS, UNPAID, NEXT_ACCRUAL.toString(), BALANCE)
        }
    }

    @Test
    fun apiError_keepsCachedValues() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(UNPAID, manager.unpaidFlow.value)
        assertEquals(NEXT_ACCRUAL, manager.nextAccrualAtFlow.value)
    }

    @Test
    fun apiError_noCache_unpaidFallsBackToZero() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFlow.value)
    }

    @Test
    fun cacheValid_sameBalanceWithinTtl_skipsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getInvestmentData(any(), any(), any()) }
        assertEquals(UNPAID, manager.unpaidFlow.value)
    }

    @Test
    fun cacheInvalid_balanceChanged_callsApi() = testScope.runTest {
        val newBalance = BigDecimal("2000")
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()
        val data = investmentData(unrealizedValue = "50", nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, newBalance)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
        assertEquals(BigDecimal("50"), manager.unpaidFlow.value)
    }

    @Test
    fun cacheInvalid_ttlExpired_callsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns 0L
        val data = investmentData(unrealizedValue = "42.5", nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun nullBalance_alwaysCallsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()
        val data = investmentData(unrealizedValue = "42.5", nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun apiReturnsNullNextAccrual_savesNull() = testScope.runTest {
        val data = investmentData(unrealizedValue = "5", nextAccrualAt = null)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertNull(manager.nextAccrualAtFlow.value)
        verify {
            localStorage.saveStackingData(COIN, ADDRESS, BigDecimal("5"), null, BALANCE)
        }
    }

    @Test
    fun cosantaWallet_usesCorrectCoinKey() = testScope.runTest {
        every { wallet.isPirateCash() } returns false
        every { wallet.isCosanta() } returns true
        val data = investmentData(unrealizedValue = "1", nextAccrualAt = null)
        coEvery { repository.getInvestmentData("cosa", ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify { repository.getInvestmentData("cosa", ADDRESS, any()) }
        verify { localStorage.saveStackingData("cosa", ADDRESS, any(), any(), any()) }
    }

    @Test
    fun malformedUnrealizedValue_doesNotCrash() = testScope.runTest {
        val data = investmentData(unrealizedValue = "not_a_number", nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFlow.value)
        verify(exactly = 0) { localStorage.saveStackingData(any(), any(), any(), any(), any()) }
    }

    private fun investmentData(
        unrealizedValue: String,
        nextAccrualAt: Instant?,
    ) = InvestmentData(
        balance = "1000",
        unrealizedValue = unrealizedValue,
        mint = "test",
        stakingActive = true,
        stakingInactiveReason = null,
        nextAccrualAt = nextAccrualAt,
    )
}

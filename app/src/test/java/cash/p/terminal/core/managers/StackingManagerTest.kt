package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.network.pirate.domain.enity.InvestmentData
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getUniqueKey
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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
        private const val WALLET_KEY = "pirate-wallet-key"
        private val BALANCE = BigDecimal("1000")
        private val NEXT_ACCRUAL = Instant.parse("2026-04-20T00:00:00Z")
        private val NEXT_PAYOUT = Instant.parse("2026-04-21T00:00:00Z")
        private val UNPAID = BigDecimal("42.5")
        private val MINT = BigDecimal("100")
    }

    @Before
    fun setup() {
        mockkStatic("cash.p.terminal.wallet.ExtensionsKt")
        every { wallet.isPirateCash() } returns true
        every { wallet.isCosanta() } returns false
        every { wallet.getUniqueKey() } returns WALLET_KEY
        every { localStorage.getStackingUnpaid(any(), any()) } returns null
        every { localStorage.getStackingMint(any(), any()) } returns null
        every { localStorage.getStackingNextAccrualAt(any(), any()) } returns null
        every { localStorage.getStackingNextPayoutAt(any(), any()) } returns null
        every { localStorage.getStackingCachedBalance(any(), any()) } returns null
        every { localStorage.getStackingTimestamp(any(), any()) } returns 0L

        manager = StackingManager(repository, localStorage, dispatcherProvider)
    }

    @Test
    fun coldStart_noCache_unpaidIsZero() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
        assertNull(manager.infoFor(wallet)?.nextAccrualAt)
    }

    @Test
    fun coldStart_hasCache_emitsCachedValues() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingMint(COIN, ADDRESS) } returns MINT
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        every { localStorage.getStackingNextPayoutAt(COIN, ADDRESS) } returns NEXT_PAYOUT.toString()
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(MINT, info?.totalIncome)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
        assertEquals(NEXT_PAYOUT, info?.nextPayoutAt)
    }

    @Test
    fun apiSuccess_updatesFlowsAndSavesCache() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = NEXT_PAYOUT,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(MINT, info?.totalIncome)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
        assertEquals(NEXT_PAYOUT, info?.nextPayoutAt)
        verify {
            localStorage.saveStackingData(
                coin = COIN,
                address = ADDRESS,
                unpaid = UNPAID,
                totalIncome = MINT,
                nextAccrualAt = NEXT_ACCRUAL.toString(),
                nextPayoutAt = NEXT_PAYOUT.toString(),
                balance = BALANCE,
            )
        }
    }

    @Test
    fun apiError_keepsCachedValues() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
    }

    @Test
    fun apiError_noCache_unpaidFallsBackToZero() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
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
        assertEquals(UNPAID, manager.unpaidFor(wallet))
    }

    @Test
    fun cacheInvalid_balanceChanged_callsApi() = testScope.runTest {
        val newBalance = BigDecimal("2000")
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()
        val data = investmentData(
            unrealizedValue = "50",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, newBalance)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
        assertEquals(BigDecimal("50"), manager.unpaidFor(wallet))
    }

    @Test
    fun cacheInvalid_ttlExpired_callsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns 0L
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun forceRefresh_sameBalanceWithinTtl_callsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingNextAccrualAt(COIN, ADDRESS) } returns NEXT_ACCRUAL.toString()
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()
        val data = investmentData(
            unrealizedValue = "50",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE, forceRefresh = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
        assertEquals(BigDecimal("50"), manager.unpaidFor(wallet))
    }

    @Test
    fun nullBalance_alwaysCallsApi() = testScope.runTest {
        every { localStorage.getStackingUnpaid(COIN, ADDRESS) } returns UNPAID
        every { localStorage.getStackingCachedBalance(COIN, ADDRESS) } returns BALANCE
        every { localStorage.getStackingTimestamp(COIN, ADDRESS) } returns System.currentTimeMillis()
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun apiReturnsNullNextAccrual_savesNull() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "5",
            mint = "0",
            nextAccrualAt = null,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertNull(manager.infoFor(wallet)?.nextAccrualAt)
        verify {
            localStorage.saveStackingData(
                coin = COIN,
                address = ADDRESS,
                unpaid = BigDecimal("5"),
                totalIncome = BigDecimal.ZERO,
                nextAccrualAt = null,
                nextPayoutAt = null,
                balance = BALANCE,
            )
        }
    }

    @Test
    fun cosantaWallet_usesCorrectCoinKey() = testScope.runTest {
        every { wallet.isPirateCash() } returns false
        every { wallet.isCosanta() } returns true
        val data = investmentData(
            unrealizedValue = "1",
            mint = "0",
            nextAccrualAt = null,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData("cosa", ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify { repository.getInvestmentData("cosa", ADDRESS, any()) }
        verify { localStorage.saveStackingData("cosa", ADDRESS, any(), any(), any(), any(), any()) }
    }

    @Test
    fun malformedUnrealizedValue_doesNotCrash() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "not_a_number",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
        verify(exactly = 0) {
            localStorage.saveStackingData(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun perWalletIsolation_pirateAndCosanta_storedSeparately() = testScope.runTest {
        val cosantaWallet = mockk<Wallet>()
        every { cosantaWallet.isPirateCash() } returns false
        every { cosantaWallet.isCosanta() } returns true
        every { cosantaWallet.getUniqueKey() } returns "cosanta-wallet-key"
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "10",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData("cosa", "CosAddr", any()) } returns investmentData(
            unrealizedValue = "5.5",
            mint = "2",
            nextAccrualAt = null,
            nextPayoutAt = NEXT_PAYOUT,
        )

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        manager.loadInvestmentData(cosantaWallet, "CosAddr", BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal("42.5"), manager.unpaidFor(wallet))
        assertEquals(BigDecimal("5.5"), manager.unpaidFor(cosantaWallet))
        assertNotEquals(manager.unpaidFor(wallet), manager.unpaidFor(cosantaWallet))
    }

    @Test
    fun unpaidFlow_emitsPerWalletValue() = testScope.runTest {
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val emitted = manager.unpaidFlow(wallet).first()
        assertEquals(UNPAID, emitted)
    }

    @Test
    fun infoFlow_unrelatedWallet_emitsNull() = testScope.runTest {
        val otherWallet = mockk<Wallet>()
        every { otherWallet.getUniqueKey() } returns "unrelated-wallet"
        every { otherWallet.isPirateCash() } returns false
        every { otherWallet.isCosanta() } returns false

        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertNull(manager.infoFor(otherWallet))
    }

    @Test
    fun unsupportedWallet_setsEmptyInfo() = testScope.runTest {
        val unsupported = mockk<Wallet>()
        every { unsupported.isPirateCash() } returns false
        every { unsupported.isCosanta() } returns false
        every { unsupported.getUniqueKey() } returns "unsupported"

        manager.loadInvestmentData(unsupported, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getInvestmentData(any(), any(), any()) }
        assertEquals(BigDecimal.ZERO, manager.unpaidFor(unsupported))
    }

    private fun investmentData(
        unrealizedValue: String,
        mint: String,
        nextAccrualAt: Instant?,
        nextPayoutAt: Instant?,
    ) = InvestmentData(
        balance = "1000",
        unrealizedValue = unrealizedValue,
        mint = mint,
        stakingActive = true,
        stakingInactiveReason = null,
        nextAccrualAt = nextAccrualAt,
        nextPayoutAt = nextPayoutAt,
    )
}

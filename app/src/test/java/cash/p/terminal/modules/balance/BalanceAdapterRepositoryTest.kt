package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.PendingBalanceCalculator
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Flowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceAdapterRepositoryTest {

    private lateinit var adapterManager: IAdapterManager
    private lateinit var balanceCache: BalanceCache
    private lateinit var pendingBalanceCalculator: PendingBalanceCalculator
    private lateinit var pendingChangedFlow: MutableSharedFlow<Unit>
    private lateinit var testWallet: Wallet

    @Before
    fun setUp() {
        pendingChangedFlow = MutableSharedFlow()

        adapterManager = mockk(relaxed = true) {
            every { adaptersReadyObservable } returns Flowable.never()
            every { walletBalanceUpdatedFlow } returns MutableSharedFlow()
            every { getAdjustedBalanceData(any()) } returns BalanceData(BigDecimal.TEN)
            every { getBalanceAdapterForWallet(any()) } returns mockk<IBalanceAdapter>(relaxed = true) {
                every { balanceStateUpdatedFlow } returns MutableSharedFlow()
                every { balanceUpdatedFlow } returns MutableSharedFlow()
            }
        }

        balanceCache = mockk(relaxed = true)

        pendingBalanceCalculator = mockk(relaxed = true)
        every { pendingBalanceCalculator.pendingChangedFlow } returns pendingChangedFlow

        testWallet = mockk(relaxed = true)
    }

    @Test
    fun updatesObservable_emitsWhenPendingTransactionsChange() {
        // Given: BalanceAdapterRepository with wallets set
        val repository = BalanceAdapterRepository(
            adapterManager,
            balanceCache,
            pendingBalanceCalculator
        )
        repository.setWallet(listOf(testWallet))

        val testObserver = repository.updatesObservable.test()

        // When: pending transaction changes
        runBlocking { pendingChangedFlow.emit(Unit) }

        // Then: updatesObservable should emit the wallet
        testObserver.awaitCount(1, { }, 1000)
        testObserver.assertValueCount(1)
        assertEquals(testWallet, testObserver.values().first())

        testObserver.dispose()
    }

    @Test
    fun updatesObservable_emitsForAllWalletsWhenPendingTransactionsChange() {
        // Given: BalanceAdapterRepository with multiple wallets
        val wallet1 = mockk<Wallet>(relaxed = true)
        val wallet2 = mockk<Wallet>(relaxed = true)
        val wallet3 = mockk<Wallet>(relaxed = true)

        val repository = BalanceAdapterRepository(
            adapterManager,
            balanceCache,
            pendingBalanceCalculator
        )
        repository.setWallet(listOf(wallet1, wallet2, wallet3))

        val testObserver = repository.updatesObservable.test()

        // When: pending transaction changes
        runBlocking { pendingChangedFlow.emit(Unit) }

        // Then: updatesObservable should emit all wallets
        testObserver.awaitCount(3, { }, 1000)
        testObserver.assertValueCount(3)
        assertTrue("Should emit wallet1", testObserver.values().contains(wallet1))
        assertTrue("Should emit wallet2", testObserver.values().contains(wallet2))
        assertTrue("Should emit wallet3", testObserver.values().contains(wallet3))

        testObserver.dispose()
    }
}

package cash.p.terminal.modules.balance

import cash.p.terminal.core.TestDispatcherProvider
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceAdapterRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

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
    fun updatesObservable_emitsWhenPendingTransactionsChange() = runTest(dispatcher) {
        val repository = createRepository()
        repository.setWallet(listOf(testWallet))

        val testObserver = repository.updatesObservable.test()

        pendingChangedFlow.emit(Unit)

        testObserver.assertValueCount(1)
        assertEquals(testWallet, testObserver.values().first())

        testObserver.dispose()
    }

    @Test
    fun updatesObservable_emitsForAllWalletsWhenPendingTransactionsChange() = runTest(dispatcher) {
        val wallet1 = mockk<Wallet>(relaxed = true)
        val wallet2 = mockk<Wallet>(relaxed = true)
        val wallet3 = mockk<Wallet>(relaxed = true)

        val repository = createRepository()
        repository.setWallet(listOf(wallet1, wallet2, wallet3))

        val testObserver = repository.updatesObservable.test()

        pendingChangedFlow.emit(Unit)

        testObserver.assertValueCount(3)
        assertTrue("Should emit wallet1", testObserver.values().contains(wallet1))
        assertTrue("Should emit wallet2", testObserver.values().contains(wallet2))
        assertTrue("Should emit wallet3", testObserver.values().contains(wallet3))

        testObserver.dispose()
    }

    private fun createRepository() = BalanceAdapterRepository(
        adapterManager,
        balanceCache,
        pendingBalanceCalculator,
        TestDispatcherProvider(dispatcher, testScope)
    )
}

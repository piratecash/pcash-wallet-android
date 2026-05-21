package cash.p.terminal.core.storage

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapProviderTransactionsStorageTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(dispatcher)
    private val dao = mockk<SwapProviderTransactionsDao>(relaxed = true)
    private val storage = SwapProviderTransactionsStorage(
        dao = dao,
        dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    )

    private fun tx(
        date: Long = 1000L,
        accountId: String = "acc-A",
    ) = SwapProviderTransaction(
        date = date,
        outgoingRecordUid = null,
        transactionId = "tx-$date",
        status = "new",
        provider = SwapProvider.PAYCORE,
        coinUidIn = "tether",
        blockchainTypeIn = "tron",
        amountIn = BigDecimal("100"),
        addressIn = "",
        coinUidOut = "rub",
        blockchainTypeOut = "fiat",
        amountOut = BigDecimal("9000"),
        addressOut = "",
        accountId = accountId,
    )

    private fun account(id: String) = Account(
        id = id,
        name = "Test",
        type = AccountType.EvmAddress("0x1"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    @Test
    fun observeAllByAccount_delegatesToDao() = runTest(dispatcher) {
        val txs = listOf(tx(date = 1, accountId = "acc-A"))
        every { dao.observeAllByAccount("acc-A") } returns flowOf(txs)

        val result = storage.observeAllByAccount("acc-A").first()

        assertEquals(txs, result)
        verify { dao.observeAllByAccount("acc-A") }
    }

    @Test
    fun observeAllByAccount_returnsEmptyForUnknownAccount() = runTest(dispatcher) {
        every { dao.observeAllByAccount("unknown") } returns flowOf(emptyList())

        val result = storage.observeAllByAccount("unknown").first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_emitsTransactionsForActiveAccount() = runTest(dispatcher) {
        val txs = listOf(tx(date = 1, accountId = "acc-A"))
        every { dao.observeAllByAccount("acc-A") } returns flowOf(txs)
        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(account("acc-A"))
        )

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(txs, result)
    }

    @Test
    fun observeForActiveAccount_emitsEmptyWhenNotLoaded() = runTest(dispatcher) {
        val accountFlow = MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_emitsEmptyWhenAccountNull() = runTest(dispatcher) {
        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(null)
        )

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_switchesWhenAccountChanges() = runTest(dispatcher) {
        val txsA = listOf(tx(date = 1, accountId = "acc-A"))
        val txsB = listOf(tx(date = 2, accountId = "acc-B"))
        every { dao.observeAllByAccount("acc-A") } returns flowOf(txsA)
        every { dao.observeAllByAccount("acc-B") } returns flowOf(txsB)

        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(account("acc-A"))
        )

        val flow = storage.observeForActiveAccount(accountFlow)

        assertEquals(txsA, flow.first())

        accountFlow.value = ActiveAccountState.ActiveAccount(account("acc-B"))

        assertEquals(txsB, flow.first())
    }
}

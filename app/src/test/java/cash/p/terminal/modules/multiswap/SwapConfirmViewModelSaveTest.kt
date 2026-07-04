package cash.p.terminal.modules.multiswap

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

/**
 * Covers the on-chain (Thorchain/Maya) completion path in [SwapConfirmViewModel.onTransactionCompleted]:
 * the record must be saved once with `outgoingRecordUid = getRecordUid()` and
 * `transactionId = canonical hash`, and the off-chain path must not go through that generic save.
 *
 * `fetchFinalQuote()` dispatches via the injected [TestDispatcherProvider], which is tied to the
 * test scheduler, so the mocked provider is stubbed to return a real quote carrying the desired
 * `swapProviderTransaction`. Awaiting `advanceUntilIdle()` after VM construction lets the init-time
 * `fetchFinalQuote()` call complete and populate `swapProviderTransaction` before driving the public
 * `onTransactionCompleted` entry point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelSaveTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val pendingMultiSwapStorage = mockk<PendingMultiSwapStorage>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true)

    private val previewWallet = WalletFactory.previewWallet()
    private val token: Token = previewWallet.token
    private val swapQuote = mockk<ISwapQuote>(relaxed = true) {
        every { tokenIn } returns token
        every { tokenOut } returns token
        every { amountIn } returns BigDecimal.ONE
    }

    private val testTransaction = SwapProviderTransaction(
        outgoingRecordUid = null,
        transactionId = "",
        status = "new",
        provider = SwapProvider.THORCHAIN,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal.ONE,
        addressIn = "address-in",
        coinUidOut = "ethereum",
        blockchainTypeOut = "ethereum",
        amountOut = BigDecimal.ONE,
        addressOut = "address-out",
    )

    private val sendTransactionServiceState = SendTransactionServiceState(
        availableBalance = null,
        networkFee = null,
        cautions = emptyList(),
        sendable = true,
        loading = false,
        fields = emptyList(),
        extraFees = emptyMap(),
    )

    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(module {
                single<ILocalStorage> { localStorage }
                single<PendingMultiSwapStorage> { pendingMultiSwapStorage }
                single<SwapProviderTransactionsStorage> { swapProviderTransactionsStorage }
                single<MarketKitWrapper> { mockk(relaxed = true) }
                single<IBalanceHiddenManager> {
                    mockk(relaxed = true) {
                        every { balanceHiddenFlow } returns MutableStateFlow(false)
                    }
                }
                single<DispatcherProvider> { TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)) }
            })
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    /** Stubs `fetchFinalQuote` to return a real quote carrying [transaction]. */
    private fun <T : IMultiSwapProvider> T.stubFetchFinalQuote(transaction: SwapProviderTransaction): T {
        every { mevProtectionAvailable } returns false
        coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } returns SwapFinalQuoteThorChain(
            tokenIn = token,
            tokenOut = token,
            amountIn = BigDecimal.ONE,
            amountOut = BigDecimal.ONE,
            amountOutMin = BigDecimal.ONE,
            sendTransactionData = SendTransactionData.Unsupported,
            priceImpact = null,
            fields = emptyList(),
            cautions = mutableListOf(),
            swapProviderTransaction = transaction,
        )
        return this
    }

    private fun createViewModel(provider: IMultiSwapProvider): SwapConfirmViewModel {
        val sendTransactionService = mockk<ISendTransactionService<Nothing>>(relaxed = true) {
            every { hasSettings() } returns false
            every { mevProtectionAvailable } returns false
            every { stateFlow } returns ServiceStateFlow(
                MutableSharedFlow<SendTransactionServiceState>(
                    replay = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ).also { it.tryEmit(sendTransactionServiceState) }.asSharedFlow()
            )
            every { sendTransactionSettingsFlow } returns MutableStateFlow(SendTransactionSettings.Common)
        }
        val adapterManager = mockk<IAdapterManager>(relaxed = true)
        val marketKit = mockk<MarketKitWrapper>(relaxed = true)
        val currencyManager = mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2, 0)
        }
        val vm = SwapConfirmViewModel(
            swapProvider = provider,
            swapQuote = swapQuote,
            swapSettings = emptyMap(),
            currencyManager = currencyManager,
            fiatServiceIn = FiatService(marketKit),
            fiatServiceOut = FiatService(marketKit),
            fiatServiceOutMin = FiatService(marketKit),
            sendTransactionService = sendTransactionService,
            timerService = TimerService(),
            priceImpactService = PriceImpactService(),
            wallet = previewWallet,
            adapterManager = adapterManager,
        )
        viewModelStore.put("test-vm", vm)
        return vm
    }

    @Test
    fun onTransactionCompleted_btcResultOnChainProvider_savesUidAndCanonicalHashSeparately() = runTest(dispatcher) {
        val provider = mockk<IMultiSwapProvider>(relaxed = true).stubFetchFinalQuote(testTransaction)
        val vm = createViewModel(provider)
        advanceUntilIdle()

        vm.onTransactionCompleted(SendTransactionResult.Btc(uid = "btc-uid", canonicalHashReversedHex = "btc-canonical-hash"))

        assertNotEquals("btc-uid", "btc-canonical-hash")
        coVerify(exactly = 1) {
            swapProviderTransactionsStorage.save(
                match {
                    it.outgoingRecordUid == "btc-uid" &&
                        it.transactionId == "btc-canonical-hash" &&
                        it.provider == SwapProvider.THORCHAIN
                }
            )
        }

        viewModelStore.clear()
        advanceUntilIdle()
    }

    @Test
    fun onTransactionCompleted_evmResultOnChainProvider_savesRecordUidAsCanonicalHash() = runTest(dispatcher) {
        val provider = mockk<IMultiSwapProvider>(relaxed = true).stubFetchFinalQuote(testTransaction)
        val vm = createViewModel(provider)
        advanceUntilIdle()

        val evmTransaction = mockk<Transaction>(relaxed = true) {
            every { hashString } returns "evm-hash"
        }
        val fullTransaction = mockk<FullTransaction>(relaxed = true) {
            every { transaction } returns evmTransaction
        }

        vm.onTransactionCompleted(SendTransactionResult.Evm(fullTransaction))

        coVerify(exactly = 1) {
            swapProviderTransactionsStorage.save(
                match { it.outgoingRecordUid == "evm-hash" && it.transactionId == "evm-hash" }
            )
        }

        viewModelStore.clear()
        advanceUntilIdle()
    }

    @Test
    fun onTransactionCompleted_offChainProvider_doesNotDoubleSave() = runTest(dispatcher) {
        val provider = mockk<OffChainSwapProvider>(relaxed = true).stubFetchFinalQuote(testTransaction)
        val vm = createViewModel(provider)
        advanceUntilIdle()

        vm.onTransactionCompleted(SendTransactionResult.Btc(uid = "btc-uid", canonicalHashReversedHex = "btc-canonical-hash"))

        verify(exactly = 1) { provider.onTransactionCompleted(any(), any()) }
        verify(exactly = 0) { swapProviderTransactionsStorage.save(any()) }

        viewModelStore.clear()
        advanceUntilIdle()
    }
}

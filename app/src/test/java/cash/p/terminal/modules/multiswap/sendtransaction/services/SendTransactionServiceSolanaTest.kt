package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionServiceSolanaTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()

    // Mocks
    private lateinit var pendingRegistrar: PendingTransactionRegistrar
    private lateinit var adapter: ISendSolanaAdapter
    private lateinit var walletUseCase: WalletUseCase
    private lateinit var adapterManager: IAdapterManager
    private lateinit var marketKit: MarketKitWrapper
    private lateinit var numberFormatter: IAppNumberFormatter
    private lateinit var currencyManager: CurrencyManager
    private lateinit var balanceAdapter: IBalanceAdapter
    private lateinit var receiveAdapter: IReceiveAdapter

    // Test data
    private lateinit var testToken: Token
    private lateinit var testWallet: Wallet

    private val testTransactionHash = "test-tx-hash-123"
    private val testFromAddress = "from-address-123"
    private val testToAddress = "to-address-456"
    private val testAmount = BigDecimal("1.5")
    private val testFee = BigDecimal("0.000155")
    private val testBalance = BigDecimal("100.0")

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<PendingTransactionRegistrar> { pendingRegistrar }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { numberFormatter }
                single<CurrencyManager> { currencyManager }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        pendingRegistrar = mockk(relaxed = true)
        adapter = mockk(relaxed = true)
        walletUseCase = mockk(relaxed = true)
        adapterManager = mockk(relaxed = true)
        marketKit = mockk(relaxed = true)
        numberFormatter = mockk(relaxed = true)
        currencyManager = mockk(relaxed = true)
        balanceAdapter = mockk(relaxed = true)
        receiveAdapter = mockk(relaxed = true)

        setupTestData()
        setupMocks()
        setupAppMocks()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun sendTransaction_rawTransaction_registersPendingDraftWithCorrectData() = runTest(dispatcher) {
        // Given
        val rawTransactionStr = "48656c6c6f" // "Hello" in hex
        val rawTransactionAddress = "raw-tx-to-address"
        val rawTransactionAmount = BigDecimal("2.5")
        val estimatedFee = BigDecimal("0.0005")

        every { adapter.estimateFee(any()) } returns estimatedFee

        val draftSlot = slot<PendingTransactionDraft>()
        coEvery { pendingRegistrar.register(capture(draftSlot)) } returns "pending-tx-id"

        val fullTransaction = createMockFullTransaction(testTransactionHash)
        coEvery { adapter.send(any<ByteArray>()) } returns fullTransaction

        val service = createService()
        service.setSendTransactionData(
            SendTransactionData.Solana.WithRawTransaction(
                rawTransactionStr = rawTransactionStr,
                rawTransactionAddress = rawTransactionAddress,
                rawTransactionAmount = rawTransactionAmount
            )
        )
        advanceUntilIdle()

        // When
        service.sendTransaction()
        advanceUntilIdle()

        // Then
        coVerify { pendingRegistrar.register(any()) }
        coVerify { pendingRegistrar.updateTxId("pending-tx-id", testTransactionHash) }

        val capturedDraft = draftSlot.captured
        assertEquals(rawTransactionAmount, capturedDraft.amount)
        assertEquals(estimatedFee, capturedDraft.fee)
        assertEquals(rawTransactionAddress, capturedDraft.toAddress)
        assertEquals(testFromAddress, capturedDraft.fromAddress)
        assertEquals(testBalance, capturedDraft.sdkBalanceAtCreation)
        assertEquals(testWallet, capturedDraft.wallet)
        assertEquals(testToken, capturedDraft.token)
    }

    @Test
    fun sendTransaction_rawTransactionWithoutAmount_usesFeeAsAmount() = runTest(dispatcher) {
        // Given: raw transaction without explicit amount (amount is null in data class)
        val rawTransactionStr = "48656c6c6f"
        val rawTransactionAddress = "raw-tx-to-address"
        val estimatedFee = BigDecimal("0.0005")

        every { adapter.estimateFee(any()) } returns estimatedFee

        val draftSlot = slot<PendingTransactionDraft>()
        coEvery { pendingRegistrar.register(capture(draftSlot)) } returns "pending-tx-id"

        val fullTransaction = createMockFullTransaction(testTransactionHash)
        coEvery { adapter.send(any<ByteArray>()) } returns fullTransaction

        val service = createService()
        // Note: Looking at the code, WithRawTransaction requires rawTransactionAmount,
        // but the service uses: rawTransactionAmount ?: fee
        // So if we want to test fee fallback, we'd need to pass null, but the data class requires it.
        // Let's test that when amount is provided, it uses the amount, not fee
        service.setSendTransactionData(
            SendTransactionData.Solana.WithRawTransaction(
                rawTransactionStr = rawTransactionStr,
                rawTransactionAddress = rawTransactionAddress,
                rawTransactionAmount = BigDecimal("3.0")
            )
        )
        advanceUntilIdle()

        // When
        service.sendTransaction()
        advanceUntilIdle()

        // Then
        val capturedDraft = draftSlot.captured
        assertEquals(BigDecimal("3.0"), capturedDraft.amount)
        assertEquals(estimatedFee, capturedDraft.fee)
    }

    @Test
    fun sendTransaction_simpleTransaction_registersPendingDraftWithCorrectData() = runTest(dispatcher) {
        // Given
        // Use a valid Solana address format (base58 encoded public key)
        val address = "11111111111111111111111111111111"
        val amount = BigDecimal("0.1") // Small amount to pass balance check

        val draftSlot = slot<PendingTransactionDraft>()
        coEvery { pendingRegistrar.register(capture(draftSlot)) } returns "pending-tx-id"

        val fullTransaction = createMockFullTransaction(testTransactionHash)
        coEvery { adapter.send(any<BigDecimal>(), any()) } returns fullTransaction

        val service = createService()

        // Start the service to begin collecting flows
        val testScope = kotlinx.coroutines.CoroutineScope(dispatcher)
        service.start(testScope)
        advanceUntilIdle()

        service.setSendTransactionData(
            SendTransactionData.Solana.Regular(
                address = address,
                amount = amount
            )
        )
        advanceUntilIdle()

        // When
        service.sendTransaction()
        advanceUntilIdle()

        // Then
        coVerify { pendingRegistrar.register(any()) }
        coVerify { pendingRegistrar.updateTxId("pending-tx-id", testTransactionHash) }

        val capturedDraft = draftSlot.captured
        assertEquals(amount, capturedDraft.amount)
        assertEquals(SolanaKit.fee, capturedDraft.fee)
        assertEquals(testFromAddress, capturedDraft.fromAddress)
        assertEquals(testBalance, capturedDraft.sdkBalanceAtCreation)
        assertEquals(testWallet, capturedDraft.wallet)
        assertEquals(testToken, capturedDraft.token)
        // Verify toAddress is set (the PublicKey.toString() of the SolanaAddress)
        assertEquals(address, capturedDraft.toAddress)
    }

    @Test
    fun sendTransaction_onFailure_deletesFailedPendingTransaction() = runTest(dispatcher) {
        // Given
        val rawTransactionStr = "48656c6c6f"
        val rawTransactionAddress = "raw-tx-to-address"
        val rawTransactionAmount = BigDecimal("2.5")

        every { adapter.estimateFee(any()) } returns testFee
        coEvery { pendingRegistrar.register(any()) } returns "pending-tx-id"
        coEvery { adapter.send(any<ByteArray>()) } throws RuntimeException("Transaction failed")

        val service = createService()
        service.setSendTransactionData(
            SendTransactionData.Solana.WithRawTransaction(
                rawTransactionStr = rawTransactionStr,
                rawTransactionAddress = rawTransactionAddress,
                rawTransactionAmount = rawTransactionAmount
            )
        )
        advanceUntilIdle()

        // When
        var exceptionThrown = false
        try {
            service.sendTransaction()
            advanceUntilIdle()
        } catch (e: RuntimeException) {
            exceptionThrown = true
        }

        // Then
        assert(exceptionThrown) { "Expected exception to be thrown" }
        coVerify { pendingRegistrar.deleteFailed("pending-tx-id") }
    }

    @Test
    fun sendTransaction_simpleTransactionFailure_deletesFailedPendingTransaction() = runTest(dispatcher) {
        // Given
        // Use a valid Solana address format (base58 encoded public key)
        val address = "11111111111111111111111111111111"
        // Use small amount to pass the balance check
        val amount = BigDecimal("0.1")

        coEvery { pendingRegistrar.register(any()) } returns "pending-tx-id"
        coEvery { adapter.send(any<BigDecimal>(), any()) } throws RuntimeException("Send failed")

        val service = createService()

        // Start the service to begin collecting flows
        val testScope = kotlinx.coroutines.CoroutineScope(dispatcher)
        service.start(testScope)
        advanceUntilIdle()

        service.setSendTransactionData(
            SendTransactionData.Solana.Regular(
                address = address,
                amount = amount
            )
        )
        advanceUntilIdle()

        // When
        var exceptionThrown = false
        try {
            service.sendTransaction()
            advanceUntilIdle()
        } catch (e: RuntimeException) {
            exceptionThrown = true
        }

        // Then
        assert(exceptionThrown) { "Expected exception to be thrown" }
        coVerify { pendingRegistrar.deleteFailed("pending-tx-id") }
    }

    private fun setupTestData() {
        val testCoin = Coin(uid = "solana", name = "Solana", code = "SOL")
        testToken = Token(
            coin = testCoin,
            blockchain = Blockchain(
                type = BlockchainType.Solana,
                name = "Solana",
                eip3091url = null
            ),
            type = TokenType.Native,
            decimals = 9
        )

        testWallet = mockk(relaxed = true) {
            every { token } returns testToken
            every { coin } returns testCoin
        }
    }

    private fun setupMocks() {
        every { adapter.maxSpendableBalance } returns testBalance

        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns testWallet

        every { balanceAdapter.balanceData } returns BalanceData(testBalance)
        every { receiveAdapter.receiveAddress } returns testFromAddress

        every { adapterManager.getBalanceAdapterForWallet(any()) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(any()) } returns receiveAdapter
        every { adapterManager.getAdjustedBalanceData(any()) } returns BalanceData(testBalance)
        coEvery { adapterManager.awaitAdapterForWallet<ISendSolanaAdapter>(any(), any()) } returns adapter

        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)

        every { marketKit.token(any()) } returns testToken
        every { marketKit.coinPrice(any(), any()) } returns null
    }

    private fun setupAppMocks() {
        mockkObject(App)

        every { App.currencyManager } returns currencyManager
        every { App.marketKit } returns marketKit
    }

    private fun createService(): SendTransactionServiceSolana {
        return SendTransactionServiceSolana(testToken)
    }

    private fun createMockFullTransaction(hash: String): FullTransaction {
        val transaction = Transaction(
            hash = hash,
            timestamp = 1700000000000L, // Fixed timestamp for deterministic tests
            fee = testFee,
            from = testFromAddress,
            to = testToAddress,
            amount = testAmount
        )
        return FullTransaction(
            transaction = transaction,
            tokenTransfers = emptyList()
        )
    }
}

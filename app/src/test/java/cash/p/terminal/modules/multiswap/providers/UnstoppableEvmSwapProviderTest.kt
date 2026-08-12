package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableApproval
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableExecution
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableSignableTx
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableToken
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

private const val SENDER_ADDRESS = "0x1111111111111111111111111111111111111111"
private const val ROUTER_ADDRESS = "0x2222222222222222222222222222222222222222"
private const val SPENDER_ADDRESS = "0x3333333333333333333333333333333333333333"
private const val OTHER_SPENDER_ADDRESS = "0x4444444444444444444444444444444444444444"
private const val OTHER_SENDER_ADDRESS = "0x5555555555555555555555555555555555555555"
private const val USDC_ADDRESS = "0x6666666666666666666666666666666666666666"
private const val ETH_CHAIN_ID = "1"
private const val WRONG_CHAIN_ID = "56"
private val AMOUNT_IN = BigDecimal("1")

@OptIn(ExperimentalCoroutinesApi::class)
class UnstoppableEvmSwapProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val repository = mockk<UnstoppableRepository>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)

    private val tokenIn = Token(
        coin = Coin(uid = "ethereum", name = "Ethereum", code = "ETH"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Native,
        decimals = 18,
    )
    private val tokenOut = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20(USDC_ADDRESS),
        decimals = 6,
    )

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount("acc-1")
        coEvery { repository.getTokens("BARTER") } returns UnstoppableProviderTokens(
            tokens = listOf(
                UnstoppableToken(chain = "ethereum", chainId = ETH_CHAIN_ID, address = null, identifier = "ETH"),
                UnstoppableToken(chain = "ethereum", chainId = ETH_CHAIN_ID, address = USDC_ADDRESS, identifier = "USDC"),
            ),
            supportedChainIds = emptyList(),
        )
        every {
            marketKit.token(match { it.blockchainType == BlockchainType.Ethereum && it.tokenType == TokenType.Native })
        } returns tokenIn
        every {
            marketKit.token(match { it.blockchainType == BlockchainType.Ethereum && it.tokenType == TokenType.Eip20(USDC_ADDRESS) })
        } returns tokenOut
        every { walletUseCase.getReceiveAddress(tokenIn) } returns SENDER_ADDRESS
    }

    private fun createProvider() = UnstoppableEvmSwapProvider(
        descriptor = UnstoppableProvider.Barter,
        walletUseCase = walletUseCase,
        repository = repository,
        marketKit = marketKit,
        accountManager = accountManager,
        dispatcherProvider = dispatcherProvider,
        providerSupport = buildOffChainSwapProviderSupport(walletUseCase, accountManager, storage, marketKit),
    )

    private fun validExecution() = UnstoppableExecution(
        method = UnstoppableExecution.METHOD_SIGNED_TRANSACTION,
        chain = ETH_CHAIN_ID,
        transactions = listOf(
            // Native ETH input of AMOUNT_IN=1 → value must be 1e18 wei (0xde0b6b3a7640000).
            UnstoppableSignableTx(
                kind = "evm",
                to = ROUTER_ADDRESS,
                from = SENDER_ADDRESS,
                value = "0xde0b6b3a7640000",
                data = "0xabcdef",
                gas = "0x5208",
            ),
        ),
        approval = UnstoppableApproval(token = null, spender = SPENDER_ADDRESS, amount = null),
        depositAddress = null,
        attachment = null,
        unsignedTx = null,
    )

    private fun routeWith(execution: UnstoppableExecution, approvalSpender: String? = SPENDER_ADDRESS) = UnstoppableRoute(
        expectedBuyAmount = BigDecimal("100"),
        minBuyAmount = null,
        estimatedTimeSeconds = null,
        approvalSpender = approvalSpender,
        execution = execution,
        uuid = "route-uuid",
    )

    private fun stubSwapResponse(route: UnstoppableRoute) {
        coEvery {
            repository.swap(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns route
    }

    private suspend fun fetchFinalQuote(provider: UnstoppableEvmSwapProvider) =
        provider.fetchFinalQuote(tokenIn, tokenOut, AMOUNT_IN, emptyMap(), null, mockk(relaxed = true))

    private suspend fun assertFetchFinalQuoteThrows(route: UnstoppableRoute, expectedMessageFragment: String) {
        stubSwapResponse(route)
        val provider = createProvider()

        val exception = try {
            fetchFinalQuote(provider)
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull("expected IllegalStateException containing '$expectedMessageFragment'", exception)
        assertTrue(exception?.message.orEmpty().contains(expectedMessageFragment))
    }

    @Test
    fun fetchFinalQuote_validExecution_buildsEvmSendTransactionData() = runTest(dispatcher) {
        stubSwapResponse(routeWith(validExecution()))
        val provider = createProvider()

        val quote = fetchFinalQuote(provider)

        val sendData = quote.sendTransactionData as SendTransactionData.Evm
        assertEquals(ROUTER_ADDRESS.lowercase(), sendData.transactionData.to.hex.lowercase())
        assertEquals(BigInteger("1000000000000000000"), sendData.transactionData.value)
        assertEquals(21000L, sendData.gasLimit)
        assertNull(quote.amountOutMin)
    }

    @Test
    fun fetchFinalQuote_nativeValueMismatch_throws() = runTest(dispatcher) {
        // Native input but the server-signed value differs from the quoted amount → reject before sending.
        val execution = validExecution().let { it.copy(transactions = listOf(it.transactions.single().copy(value = "0x1"))) }
        assertFetchFinalQuoteThrows(routeWith(execution), "does not match expected")
    }

    @Test
    fun fetchFinalQuote_usesOutputTokenReceiveAddressAsDestination() = runTest(dispatcher) {
        val recipient = "0x9999999999999999999999999999999999999999"
        every { walletUseCase.getReceiveAddress(tokenOut) } returns recipient
        val destinationSlot = slot<String>()
        coEvery {
            repository.swap(any(), any(), any(), any(), any(), capture(destinationSlot), any(), any(), any())
        } returns routeWith(validExecution())

        fetchFinalQuote(createProvider())

        // P1: the committed order must pay out to the OUTPUT token's receive address.
        assertEquals(recipient, destinationSlot.captured)
    }

    @Test
    fun fetchFinalQuote_transferMethod_throws() = runTest(dispatcher) {
        val execution = validExecution().copy(method = UnstoppableExecution.METHOD_TRANSFER)
        assertFetchFinalQuoteThrows(routeWith(execution), "unexpected execution method")
    }

    @Test
    fun fetchFinalQuote_wrongSignableKind_throws() = runTest(dispatcher) {
        val execution = validExecution().let { it.copy(transactions = listOf(it.transactions.single().copy(kind = "solana"))) }
        assertFetchFinalQuoteThrows(routeWith(execution), "unexpected signable kind")
    }

    @Test
    fun fetchFinalQuote_chainMismatch_throws() = runTest(dispatcher) {
        val execution = validExecution().copy(chain = WRONG_CHAIN_ID)
        assertFetchFinalQuoteThrows(routeWith(execution), "does not match tokenIn chain")
    }

    @Test
    fun fetchFinalQuote_fromMismatch_throws() = runTest(dispatcher) {
        val execution = validExecution().let { it.copy(transactions = listOf(it.transactions.single().copy(from = OTHER_SENDER_ADDRESS))) }
        assertFetchFinalQuoteThrows(routeWith(execution), "does not match sending address")
    }

    @Test
    fun fetchFinalQuote_missingToAddress_throws() = runTest(dispatcher) {
        val execution = validExecution().let { it.copy(transactions = listOf(it.transactions.single().copy(to = null))) }
        assertFetchFinalQuoteThrows(routeWith(execution), "has no `to` address")
    }

    @Test
    fun fetchFinalQuote_approvalSpenderMismatch_throws() = runTest(dispatcher) {
        assertFetchFinalQuoteThrows(routeWith(validExecution(), approvalSpender = OTHER_SPENDER_ADDRESS), "approval spender mismatch")
    }
}

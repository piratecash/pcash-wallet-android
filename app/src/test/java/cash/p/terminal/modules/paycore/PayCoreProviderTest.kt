package cash.p.terminal.modules.paycore

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PayCoreProviderTest {

    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val apiService = mockk<PayCoreApiService>(relaxed = true)
    private val secureStorage = mockk<PayCoreSecureStorage>(relaxed = true)
    private val featureToggle = mockk<PayCoreFeatureToggle>()
    private val accountManager = mockk<IAccountManager>()
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()

    private val rubToken = PayCoreAssets.rubToken

    private val usdtToken = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT", marketCapRank = null, coinGeckoId = null, image = null),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        decimals = 6
    )

    private val mnemonicAccount = mockk<Account> {
        every { type } returns mockk<AccountType.Mnemonic>()
        every { id } returns "test-account"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { featureToggle.isEnabled() } returns true
        every { accountManager.activeAccount } returns mnemonicAccount
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun supports_rubToUsdt_returnsTrue() = runTest {
        val provider = createProvider()
        assertTrue(provider.supports(rubToken, usdtToken))
    }

    @Test
    fun supports_usdtToRub_returnsTrue() = runTest {
        val provider = createProvider()
        assertTrue(provider.supports(usdtToken, rubToken))
    }

    @Test
    fun supports_bscUsdToRub_returnsFalse() = runTest {
        val bscUsdToken = Token(
            coin = Coin(uid = "tether", name = "BSC-USD", code = "BSC-USD", marketCapRank = null, coinGeckoId = null, image = null),
            blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null),
            type = TokenType.Eip20("0x55d398326f99059fF775485246999027B3197955"),
            decimals = 18
        )
        val provider = createProvider()
        assertFalse(provider.supports(bscUsdToken, rubToken))
    }

    @Test
    fun supports_usdtToUsdt_returnsFalse() = runTest {
        val provider = createProvider()
        assertFalse(provider.supports(usdtToken, usdtToken))
    }

    @Test
    fun supports_featureDisabled_returnsFalse() = runTest {
        every { featureToggle.isEnabled() } returns false

        val provider = createProvider()
        assertFalse(provider.supports(rubToken, usdtToken))
    }

    @Test
    fun supports_watchOnlyAccount_returnsFalse() = runTest {
        val watchAccount = mockk<Account> {
            every { type } returns mockk<AccountType.EvmAddress>()
        }
        every { accountManager.activeAccount } returns watchAccount

        val provider = createProvider()
        assertFalse(provider.supports(rubToken, usdtToken))
    }

    @Test
    fun resolveActionRequired_fiatWalletMissing_returnsNull() = runTest {
        every { walletUseCase.getWallet(rubToken) } returns null
        every { walletUseCase.getWallet(usdtToken) } returns mockk<Wallet>()
        every { secureStorage.getVerificationStatus() } returns VerificationStatus.VERIFIED

        val provider = createProvider()
        val quote = fetchQuoteWithMockedRate(provider)

        assertNull(quote.actionRequired)
    }

    @Test
    fun resolveActionRequired_cryptoWalletMissing_returnsActionCreate() = runTest {
        every { walletUseCase.getWallet(rubToken) } returns mockk<Wallet>()
        every { walletUseCase.getWallet(usdtToken) } returns null
        every { secureStorage.getVerificationStatus() } returns VerificationStatus.VERIFIED

        val provider = createProvider()
        val quote = fetchQuoteWithMockedRate(provider)

        assertNotNull(quote.actionRequired)
        assertEquals("ActionCreate", quote.actionRequired?.javaClass?.simpleName)
    }

    @Test
    fun resolveActionRequired_verified_returnsNull() = runTest {
        every { walletUseCase.getWallet(any<Token>()) } returns mockk<Wallet>()
        every { secureStorage.getVerificationStatus() } returns VerificationStatus.VERIFIED

        val provider = createProvider()
        val quote = fetchQuoteWithMockedRate(provider)

        assertNull(quote.actionRequired)
    }

    @Test
    fun onTransactionCompleted_processPayOutSucceeds_savesPlaceholderThenMigrates() = runTest {
        val txHash = "0xdeadbeef"
        val payoutId = "payout-1"
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns txHash
        }
        coEvery { apiService.processPayOut(any(), "ERC20") } returns PayCorePayoutProcessResponse(
            status = 0,
            url = "https://pirate.paycore.pw/payout/$payoutId"
        )

        val provider = createProvider()
        prepareFinalQuote(provider)

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        coVerify(exactly = 1) { apiService.processPayOut(match { it.transactionHash == txHash }, "ERC20") }
        verify(exactly = 1) {
            storage.save(match { it.outgoingRecordUid == txHash && it.transactionId == txHash })
        }
        verify(exactly = 1) { storage.updateTransactionId(any(), payoutId) }
    }

    @Test
    fun onTransactionCompleted_processPayOutFailsThenSucceeds_migratesAfterRetry() = runTest {
        val txHash = "0xdeadbeef"
        val payoutId = "payout-after-retry"
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns txHash
        }
        coEvery { apiService.processPayOut(any(), "ERC20") } throwsMany listOf(
            RuntimeException("transient")
        ) andThen PayCorePayoutProcessResponse(
            status = 0,
            url = "https://pirate.paycore.pw/payout/$payoutId"
        )

        val provider = createProvider()
        prepareFinalQuote(provider)

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        coVerify(exactly = 2) { apiService.processPayOut(any(), "ERC20") }
        verify(exactly = 1) { storage.save(match { it.transactionId == txHash }) }
        verify(exactly = 1) { storage.updateTransactionId(any(), payoutId) }
    }

    @Test
    fun onTransactionCompleted_allRetriesFail_keepsPlaceholderWithoutMigration() = runTest {
        val txHash = "0xdeadbeef"
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns txHash
        }
        coEvery { apiService.processPayOut(any(), "ERC20") } throws RuntimeException("offline")

        val provider = createProvider()
        prepareFinalQuote(provider)

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        coVerify(exactly = 4) { apiService.processPayOut(any(), "ERC20") }
        verify(exactly = 1) { storage.save(match { it.transactionId == txHash }) }
        verify(exactly = 0) { storage.updateTransactionId(any(), any()) }
    }

    @Test
    fun onTransactionCompleted_responseWithoutPayoutId_keepsPlaceholderWithoutMigration() = runTest {
        val txHash = "0xdeadbeef"
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns txHash
        }
        coEvery { apiService.processPayOut(any(), "ERC20") } returns PayCorePayoutProcessResponse(
            status = 0,
            url = null
        )

        val provider = createProvider()
        prepareFinalQuote(provider)

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        verify(exactly = 1) { storage.save(match { it.transactionId == txHash }) }
        verify(exactly = 0) { storage.updateTransactionId(any(), any()) }
    }

    @Test
    fun onTransactionCompleted_nullTxHash_doesNothing() = runTest {
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns null
        }

        val provider = createProvider()
        prepareFinalQuote(provider)

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.processPayOut(any(), any()) }
        verify(exactly = 0) { storage.save(any()) }
    }

    @Test
    fun fetchQuote_amountOutOfRange_throwsSwapAmountOutOfRange() = runTest {
        coEvery { apiService.getRate(any(), any(), any(), any()) } throws
            PayCoreAmountOutOfRangeException("the amount in rubles is less than the specified limit")

        val provider = createProvider()

        val thrown: Throwable? = try {
            provider.fetchQuote(rubToken, usdtToken, BigDecimal("10"), emptyMap())
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(
            "Expected SwapAmountOutOfRange but got $thrown",
            thrown is SwapAmountOutOfRange
        )
    }

    @Test
    fun fetchFinalQuote_amountOutOfRange_throwsSwapAmountOutOfRange() = runTest {
        coEvery { apiService.getRate(any(), any(), any(), any()) } throws
            PayCoreAmountOutOfRangeException("the amount in rubles is more than the specified limit")

        val provider = createProvider()

        val thrown: Throwable? = try {
            provider.fetchFinalQuote(
                usdtToken,
                rubToken,
                BigDecimal.ONE,
                emptyMap(),
                null,
                mockk()
            )
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(
            "Expected SwapAmountOutOfRange but got $thrown",
            thrown is SwapAmountOutOfRange
        )
    }

    @Test
    fun onTransactionCompleted_withoutPriorFinalQuote_doesNothing() = runTest {
        val sendResult = mockk<SendTransactionResult> {
            every { getRecordUid() } returns "0xdeadbeef"
        }

        val provider = createProvider()
        // no prepareFinalQuote() → swapProviderTransaction is null

        provider.onTransactionCompleted(sendResult)
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.processPayOut(any(), any()) }
        verify(exactly = 0) { storage.save(any()) }
    }

    private suspend fun fetchQuoteWithMockedRate(provider: PayCoreProvider): PayCoreQuote {
        coEvery { apiService.getRate(any(), any(), any(), any()) } returns PayCoreRateResponse(
            currencyFrom = "RUB",
            currencyTo = "ERC20",
            amountFrom = "100",
            amountTo = "1.1",
            rate = "0.011"
        )

        return provider.fetchQuote(rubToken, usdtToken, BigDecimal("100"), emptyMap()) as PayCoreQuote
    }

    private suspend fun prepareFinalQuote(provider: PayCoreProvider) {
        coEvery { apiService.getRate(any(), any(), any(), any()) } returns PayCoreRateResponse(
            currencyFrom = "ERC20", currencyTo = "RUB",
            amountFrom = "1", amountTo = "100", rate = "100"
        )
        coEvery { apiService.getPayoutAddress(any()) } returns PayCorePayoutAddressResponse(
            address = "0x000000000000000000000000000000000000dEaD", networkType = "ERC20"
        )
        coEvery { walletUseCase.getReceiveAddress(any()) } returns "0xMyAddr"

        val adapter = mockk<cash.p.terminal.core.ISendEthereumAdapter> {
            every { getTransactionData(any(), any()) } returns mockk()
        }
        every { adapterManager.getAdapterForToken<cash.p.terminal.core.ISendEthereumAdapter>(any()) } returns adapter

        provider.fetchFinalQuote(usdtToken, rubToken, BigDecimal.ONE, emptyMap(), null, mockk())
    }

    private fun createProvider() = PayCoreProvider(
        walletUseCase = walletUseCase,
        apiService = apiService,
        secureStorage = secureStorage,
        featureToggle = featureToggle,
        accountManager = accountManager,
        adapterManager = adapterManager,
        swapProviderTransactionsStorage = storage,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )
}

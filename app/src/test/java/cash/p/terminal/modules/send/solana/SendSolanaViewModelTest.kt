package cash.p.terminal.modules.send.solana

import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.OfflineSolanaSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineSolanaTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.solanakit.SolanaKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal
import io.horizontalsystems.solanakit.models.Address as SolanaAddress

@OptIn(ExperimentalCoroutinesApi::class)
class SendSolanaViewModelTest : KoinTest {

    private interface TestSendSolanaAdapter : ISendSolanaAdapter, OfflineTransactionAdapter<SignedOfflineSolanaTransaction>

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<TestSendSolanaAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val amountService = mockk<SendAmountService>(relaxed = true)
    private val addressService = mockk<SendSolanaAddressService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private lateinit var amountStateFlow: MutableStateFlow<SendAmountService.State>
    private lateinit var addressStateFlow: MutableStateFlow<SendSolanaAddressService.State>

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
    private val solana = Blockchain(BlockchainType.Solana, "Solana", null)
    private val solanaToken = Token(
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        blockchain = solana,
        type = TokenType.Native,
        decimals = 9,
    )
    private val wallet = checkNotNull(walletFactory.create(solanaToken, account, null))
    private val amount = BigDecimal("1.2")
    private val address = Address("11111111111111111111111111111111")

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single<MarketKitWrapper> { marketKit }
                single { poisonAddressManager }
                single { locallyCreatedTransactionRepository }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        amountStateFlow = MutableStateFlow(createAmountState())
        addressStateFlow = MutableStateFlow(createAddressState())

        every { amountService.stateFlow } returns amountStateFlow
        every { addressService.stateFlow } returns addressStateFlow
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.signOffline(any()) } returns SignedOfflineSolanaTransaction(
            rawHex = "deadbeef",
            txHash = SOLANA_SIGNATURE,
            fee = SolanaKit.fee,
            blockHash = "block-hash",
            lastValidBlockHeight = 123L,
        )
        every { amountService.setAmount(any()) } answers {
            val value = firstArg<BigDecimal?>()
            amountStateFlow.value = createAmountState(
                amount = value,
                canBeSend = value != null && value > BigDecimal.ZERO,
            )
        }
        every { addressService.setAddress(any()) } answers {
            val value = firstArg<Address?>()
            addressStateFlow.value = createAddressState(
                address = value,
                canBeSend = value != null,
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_validTransaction_savesSolanaRetryMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(solanaToken, draft.wallet.token)
        assertEquals(solanaToken, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(SOLANA_SIGNATURE, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
        assertTrue(draft.inputOutpoints.isEmpty())
        assertEquals("block-hash", draft.solanaRetryMetadata?.blockHash)
        assertEquals(123L, draft.solanaRetryMetadata?.lastValidBlockHeight)
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun onClickSignOffline_splTransaction_savesSplWalletAndSolFeeToken() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val splToken = splToken()
        val splWallet = createWallet(splToken)
        val viewModel = createViewModel(
            wallet = splWallet,
            feeToken = solanaToken,
        )
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(splToken, draft.wallet.token)
        assertEquals(solanaToken, draft.feeToken)
        assertEquals(amount, draft.amount)
        assertEquals(SolanaKit.fee, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineSolanaSignRequest &&
                        it.amount == amount &&
                        it.address == address.hex
                }
            )
        }
    }

    @Test
    fun onClickSignOffline_userCancelled_resetsStateSilently() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws TrezorCancelledException()
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertEquals(OfflineSignState.Idle, viewModel.offlineSignState)
        coVerify(exactly = 0) { offlineSignedTransactionRepository.save(any(), any()) }
    }

    @Test
    fun offlineSignSupported_watchAccount_returnsFalse() {
        val watchAccount = account(AccountType.SolanaAddress(address.hex))
        val watchWallet = createWallet(solanaToken, watchAccount)

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        feeToken: Token = solanaToken,
    ) = SendSolanaViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        solBalance = BigDecimal.TEN,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = wallet.token.decimals,
        showAddressInput = true,
        address = null,
        contactsRepo = contactsRepository,
        connectivityManager = connectivityManager,
        pendingRegistrar = pendingRegistrar,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        recentAddressManager = recentAddressManager,
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun createAmountState(
        amount: BigDecimal? = null,
        canBeSend: Boolean = false,
    ) = SendAmountService.State(
        amount = amount,
        amountCaution = null,
        availableBalance = BigDecimal.TEN,
        canBeSend = canBeSend,
    )

    private fun createAddressState(
        address: Address? = null,
        canBeSend: Boolean = false,
    ) = SendSolanaAddressService.State(
        address = address,
        solanaAddress = address?.let { SolanaAddress(it.hex) },
        addressError = null,
        canBeSend = canBeSend,
    )

    private fun splToken() = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = solana,
        type = TokenType.Spl("usdc-mint"),
        decimals = 6,
    )

    private fun createWallet(token: Token, account: Account = this.account) =
        checkNotNull(walletFactory.create(token, account, null))

    private fun account(type: AccountType) = Account(
        id = "account-${type::class.simpleName}",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val SOLANA_SIGNATURE =
            "7jMAQMhBNsY4eqqGVRYP9ddHbR1vrMvF5qWZbGzMbfyqGzHGmhrxXfQnk74T9JbX8FD9Fyi7Jw1pB8HgZCkP1KKL"
    }
}

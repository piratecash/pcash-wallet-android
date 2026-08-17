package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.modules.send.monero.SendMoneroFeeService
import cash.p.terminal.modules.send.ton.FeeStatus
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException

class SendTransactionServiceMoneroTest : KoinTest {
    private lateinit var walletUseCase: WalletUseCase
    private lateinit var adapterManager: IAdapterManager
    private lateinit var marketKit: MarketKitWrapper
    private lateinit var currencyManager: CurrencyManager
    private lateinit var adapter: ISendMoneroAdapter
    private lateinit var wallet: Wallet
    private lateinit var token: Token
    private lateinit var spendReadiness: MutableStateFlow<MoneroSpendReadiness>

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<PendingTransactionRegistrar> { mockk(relaxed = true) }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<CurrencyManager> { currencyManager }
                single<IAccountManager> { mockk(relaxed = true) }
            },
        )
    }

    @Before
    fun setUp() {
        walletUseCase = mockk(relaxed = true)
        adapterManager = mockk(relaxed = true)
        marketKit = mockk(relaxed = true)
        currencyManager = mockk(relaxed = true)
        adapter = mockk(relaxed = true)
        spendReadiness = MutableStateFlow(MoneroSpendReadiness.Ready)
        token = moneroToken()
        wallet = mockk(relaxed = true) {
            every { token } returns this@SendTransactionServiceMoneroTest.token
            every { coin } returns this@SendTransactionServiceMoneroTest.token.coin
        }
        every { adapter.hardwareWallet } returns true
        every { adapter.spendReadiness } returns spendReadiness
        every { adapter.balanceData } returns BalanceData(BALANCE)
        every { adapter.maxSpendableBalance } returns BALANCE
        every { adapterManager.getAdjustedBalanceData(wallet) } returns BalanceData(BALANCE)
        every { marketKit.token(any()) } returns token
        every { marketKit.coinPrice(any(), any()) } returns null
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)
        coEvery { walletUseCase.createWalletIfNotExists(token) } returns wallet
        coEvery {
            adapterManager.awaitAdapterForWallet<ISendMoneroAdapter>(wallet, any())
        } returns adapter
        mockkObject(App)
        every { App.marketKit } returns marketKit
        every { App.currencyManager } returns currencyManager
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun checkFeeBalance_hardwareWalletHasFeeBalance_keepsSendableFeeState() {
        val service = SendTransactionServiceMonero(token)

        service.checkFeeBalance(FEE)

        assertTrue(service.hasEnoughFeeAmount())
    }

    @Test
    fun checkFeeBalance_hardwareWalletFeeExceedsBalance_blocksSendableFeeState() {
        val service = SendTransactionServiceMonero(token)

        service.checkFeeBalance(BALANCE + BigDecimal.ONE)

        assertFalse(service.hasEnoughFeeAmount())
    }

    @Test
    fun updateCautions_hardwareCancellation_keepsRetryUnblocked() {
        val service = SendTransactionServiceMonero(token)

        service.updateCautions(
            HardwareWalletOperationException(
                HardwareWalletErrorCode.Cancelled,
                "cancelled",
            ),
        )

        assertTrue(service.stateFlow.value.cautions.isEmpty())
    }

    @Test
    fun createState_keyImageSyncRequired_preservesAvailableBalance() {
        val service = SendTransactionServiceMonero(token)
        spendReadiness.value = MoneroSpendReadiness.NeedsKeyImageSync

        val state = service.stateFlow.value

        assertEquals(BALANCE, state.availableBalance)
        assertEquals(MoneroSpendReadiness.NeedsKeyImageSync, state.moneroSpendReadiness)
    }

    @Test
    fun updateWithTrezor_hardwareMonero_delegatesToPhase3Operation() = runTest {
        val service = SendTransactionServiceMonero(token)

        service.updateWithTrezor()

        coVerify(exactly = 1) { adapter.refreshHardwareKeyImages() }
    }

    private fun SendTransactionServiceMonero.checkFeeBalance(fee: BigDecimal) {
        javaClass.getDeclaredMethod(
            "checkFeeBalance",
            SendMoneroFeeService.State::class.java,
        ).apply { isAccessible = true }
            .invoke(this, SendMoneroFeeService.State(FeeStatus.Success(fee), false))
    }

    private fun SendTransactionServiceMonero.hasEnoughFeeAmount(): Boolean =
        javaClass.getDeclaredField("hasEnoughFeeAmount")
            .apply { isAccessible = true }
            .getBoolean(this)

    private fun SendTransactionServiceMonero.updateCautions(error: Throwable) {
        javaClass.getDeclaredMethod("updateCautions", Throwable::class.java)
            .apply { isAccessible = true }
            .invoke(this, error)
    }

    private fun moneroToken() = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = Blockchain(
            type = BlockchainType.Monero,
            name = "Monero",
            eip3091url = null,
        ),
        type = TokenType.Native,
        decimals = 12,
    )

    private companion object {
        val BALANCE = BigDecimal("0.02")
        val FEE = BigDecimal("0.0001")
    }
}

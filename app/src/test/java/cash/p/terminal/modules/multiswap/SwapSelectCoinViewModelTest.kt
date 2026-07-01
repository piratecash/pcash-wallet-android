package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.App
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.SystemLanguageProvider
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.modules.paycore.PayCoreFeatureToggle
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapSelectCoinViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))

    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)

    private val usdtErc20 = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        decimals = 6
    )

    private val mnemonicAccount = mockk<Account> {
        every { type } returns mockk<AccountType.Mnemonic>()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(App)
        every { App.marketKit } returns marketKit
        every { App.walletManager } returns walletManager
        every { App.adapterManager } returns adapterManager
        every { App.currencyManager } returns currencyManager
        every { walletManager.activeWallets } returns emptyList()
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun uiState_otherUsdtAppEnglishSystemUkrainian_hidesRubFiatOption() {
        val languageManager = mockk<ILanguageManager> { every { currentLanguage } returns "en" }
        val systemLanguageProvider = mockk<SystemLanguageProvider> { every { language } returns "uk" }
        val realToggle = PayCoreFeatureToggle(languageManager, systemLanguageProvider)

        val viewModel = SwapSelectCoinViewModel(
            otherSelectedToken = usdtErc20,
            activeAccount = mnemonicAccount,
            payCoreFeatureToggle = realToggle,
            dispatcherProvider = dispatcherProvider
        )

        assertFalse(viewModel.uiState.hasFiatSection)
        assertFalse(viewModel.uiState.fiatItems.any { PayCoreAssets.isRub(it.token) })
    }
}

package cash.p.terminal.modules.backuplocal

import android.util.Base64
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.managers.MarketFavoritesManager
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSettingsDao
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.ChartIndicators
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSourceBackup
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSources
import cash.p.terminal.modules.backuplocal.fullbackup.Settings
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupItem
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.solanakit.models.RpcSource
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Holds the shared [BackupProvider] test fixture: mocked dependencies, JUnit lifecycle
 * setup/teardown, and the restore-filtering helpers reused by
 * [BackupProviderV4BinaryTest] and [BackupProviderDeclinedTokensTest].
 */
internal abstract class BackupProviderRestoreTestFixture {

    protected lateinit var backupProvider: BackupProvider

    protected val localStorage: ILocalStorage = mockk(relaxed = true)
    protected val languageManager: LanguageManager = mockk(relaxed = true)
    protected val walletStorage: IEnabledWalletStorage = mockk(relaxed = true)
    protected val settingsManager: RestoreSettingsManager = mockk(relaxed = true)
    protected val accountManager: IAccountManager = mockk(relaxed = true)
    protected val accountFactory: IAccountFactory = mockk(relaxed = true)
    protected val walletManager: IWalletManager = mockk(relaxed = true)
    protected val restoreSettingsManager: RestoreSettingsManager = mockk(relaxed = true)
    protected val blockchainSettingsStorage: BlockchainSettingsStorage = mockk(relaxed = true)
    protected val evmBlockchainManager: EvmBlockchainManager = mockk(relaxed = true)
    protected val marketFavoritesManager: MarketFavoritesManager = mockk(relaxed = true)
    protected val balanceViewTypeManager: BalanceViewTypeManager = mockk(relaxed = true)
    protected val appIconService: AppIconService = mockk(relaxed = true)
    protected val themeService: ThemeService = mockk(relaxed = true)
    protected val chartIndicatorManager: ChartIndicatorManager = mockk(relaxed = true)
    protected val chartIndicatorSettingsDao: ChartIndicatorSettingsDao = mockk(relaxed = true)
    protected val balanceHiddenManager: BalanceHiddenManager = mockk(relaxed = true)
    protected val baseTokenManager: BaseTokenManager = mockk(relaxed = true)
    protected val launchScreenService: LaunchScreenService = mockk(relaxed = true)
    protected val currencyManager: CurrencyManager = mockk(relaxed = true)
    protected val btcBlockchainManager: BtcBlockchainManager = mockk(relaxed = true)
    protected val evmSyncSourceManager: EvmSyncSourceManager = mockk(relaxed = true)
    protected val evmSyncSourceStorage: EvmSyncSourceStorage = mockk(relaxed = true)
    protected val solanaRpcSourceManager: SolanaRpcSourceManager = mockk(relaxed = true)
    protected val contactsRepository: ContactsRepository = mockk(relaxed = true)
    protected val marketKit: MarketKitWrapper = mockk(relaxed = true)

    @Before
    fun setUp() {
        // Creating a real V4 binary goes through EncryptDecryptManager.generateMac, which needs
        // EthereumKit's ETH-KECCAK-256 digest.
        installEthereumCryptoProviderForTest()

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }

        every { accountManager.accounts } returns emptyList()
        every { marketFavoritesManager.getAll() } returns emptyList()
        every { btcBlockchainManager.allBlockchains } returns emptyList()
        every { evmBlockchainManager.allBlockchains } returns emptyList()
        every { contactsRepository.contacts } returns emptyList()
        every { chartIndicatorSettingsDao.getAllBlocking() } returns emptyList()

        // Nothing is known to MarketKit unless a test says otherwise
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
        stopKoin()
        startKoin {
            modules(
                module {
                    single { marketKit }
                }
            )
        }

        every { balanceViewTypeManager.balanceViewType } returns BalanceViewType.CoinThenFiat
        every { balanceViewTypeManager.balanceViewTypeFlow } returns MutableStateFlow(BalanceViewType.CoinThenFiat)

        val mockCurrency = Currency("USD", "$", 2, 0)
        every { currencyManager.baseCurrency } returns mockCurrency

        val mockRpcSource = mockk<RpcSource>(relaxed = true)
        every { mockRpcSource.name } returns "Solana RPC"
        every { solanaRpcSourceManager.rpcSource } returns mockRpcSource

        backupProvider = BackupProvider(
            localStorage = localStorage,
            languageManager = languageManager,
            walletStorage = walletStorage,
            settingsManager = settingsManager,
            accountManager = accountManager,
            accountFactory = accountFactory,
            walletManager = walletManager,
            restoreSettingsManager = restoreSettingsManager,
            blockchainSettingsStorage = blockchainSettingsStorage,
            evmBlockchainManager = evmBlockchainManager,
            marketFavoritesManager = marketFavoritesManager,
            balanceViewTypeManager = balanceViewTypeManager,
            appIconService = appIconService,
            themeService = themeService,
            chartIndicatorManager = chartIndicatorManager,
            chartIndicatorSettingsDao = chartIndicatorSettingsDao,
            balanceHiddenManager = balanceHiddenManager,
            baseTokenManager = baseTokenManager,
            launchScreenService = launchScreenService,
            currencyManager = currencyManager,
            btcBlockchainManager = btcBlockchainManager,
            evmSyncSourceManager = evmSyncSourceManager,
            evmSyncSourceStorage = evmSyncSourceStorage,
            solanaRpcSourceManager = solanaRpcSourceManager,
            contactsRepository = contactsRepository
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
        stopKoin()
    }

    protected val usdtQueryId = "ethereum|eip20:0xdac17f958d2ee523a2206206994597c13d831ec7"
    protected val daiQueryId = "ethereum|eip20:0x6b175474e89094c44da98b954eedeac495271d0f"
    protected val scamQueryId = "ethereum|eip20:0x000000000000000000000000000000000000dead"

    /** A contract the curated catalog never resolves — what Add Token leaves behind. */
    protected val manuallyAddedQueryId = "ethereum|eip20:0x000000000000000000000000000000000000beef"

    protected val restoredAccount = Account(
        id = "restored-account-id",
        name = "Restored",
        type = AccountType.Mnemonic(List(12) { "abandon" }, ""),
        origin = AccountOrigin.Restored,
        level = 0
    )

    protected fun backedUpWallet(
        tokenQueryId: String,
        settings: Map<RestoreSettingType, String>? = null,
        decimals: Int = 6
    ) = BackupLocalModule.EnabledWalletBackup(
        tokenQueryId = tokenQueryId,
        coinName = "Tether",
        coinCode = "USDT",
        decimals = decimals,
        settings = settings
    )

    /** Defaults to [BackupSource.Legacy]; tests that assert on provenance pass [source] explicitly. */
    protected fun walletBackupItem(
        enabledWallets: List<BackupLocalModule.EnabledWalletBackup>,
        account: Account = restoredAccount,
        source: BackupSource = BackupSource.Legacy
    ) = WalletBackupItem(account = account, enabledWallets = enabledWallets, source = source)

    /** Makes MarketKit resolve exactly [tokenQueryIds], mirroring the curated coin database. */
    protected fun curate(vararg tokenQueryIds: String) {
        val curated = tokenQueryIds.associateWith { id ->
            Token(
                coin = Coin(uid = id, name = "Curated", code = "CUR"),
                blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
                type = requireNotNull(TokenQuery.fromId(id)).tokenType,
                decimals = 6
            )
        }
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().mapNotNull { curated[it.id] }
        }
    }

    /** A curated Solana SPL token resolving to [TokenType.Unsupported], with no catalog decimals. */
    protected fun unsupportedCuratedSplToken(): Pair<String, Token> {
        val address = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3"
        val splQueryId = TokenQuery(BlockchainType.Solana, TokenType.Spl(address)).id
        val curated = Token(
            coin = Coin(uid = "pyth-network", name = "Pyth Network", code = "PYTH"),
            blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
            type = TokenType.Unsupported("spl", address),
            decimals = 0
        )
        return splQueryId to curated
    }

    protected fun captureRestoredWallets(): CapturingSlot<List<EnabledWallet>> {
        val saved = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(saved)) } returns Unit
        return saved
    }

    /** A minimal but complete [Settings], with one custom EVM sync source to prove custom RPC restore ran. */
    protected fun minimalSettings() = Settings(
        balanceViewType = BalanceViewType.CoinThenFiat,
        appIcon = "main",
        currentTheme = ThemeType.System,
        chartIndicatorsEnabled = false,
        chartIndicators = ChartIndicators(rsi = emptyList(), ma = emptyList(), macd = emptyList()),
        balanceAutoHidden = false,
        conversionTokenQueryId = null,
        language = "en",
        launchScreen = LaunchPage.Auto,
        marketsTabEnabled = true,
        balanceHideButtons = false,
        baseCurrency = "USD",
        btcModes = emptyList(),
        priceChangeMode = PriceChangeInterval.LAST_24H,
        evmSyncSources = EvmSyncSources(
            selected = emptyList(),
            custom = listOf(
                EvmSyncSourceBackup(
                    blockchainTypeId = BlockchainType.Ethereum.uid,
                    url = "https://rpc.example.com",
                    auth = null
                )
            )
        ),
        solanaSyncSource = null
    )
}

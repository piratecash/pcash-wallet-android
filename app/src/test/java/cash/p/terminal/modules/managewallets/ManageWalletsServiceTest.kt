package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class ManageWalletsServiceTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>()
    private val restoreSettingsService = mockk<RestoreSettingsService>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)
    private val rescanMoneroUseCase = mockk<RescanMoneroUseCase>(relaxed = true)
    private val walletFactory = mockk<WalletFactory>()
    private val hardwarePublicKeyProvider =
        mockk<GetHardwarePublicKeyForWalletUseCase>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)

    private val approveSettingsSubject =
        PublishSubject.create<RestoreSettingsService.TokenWithSettings>()
    private val rejectSettingsSubject = PublishSubject.create<Token>()
    private val activeWalletsFlow = MutableStateFlow<List<Wallet>>(emptyList())

    private val account = account()
    private val zcashTokens = zcashTokens()
    private var activeWallets = emptyList<Wallet>()
    private lateinit var service: ManageWalletsService

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { walletFactory }
                single { hardwarePublicKeyProvider }
                single<MarketKitWrapper> { marketKit }
                single { offlineModeUseCase }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        every { restoreSettingsService.approveSettingsObservable } returns approveSettingsSubject
        every { restoreSettingsService.rejectApproveSettingsObservable } returns rejectSettingsSubject
        every { walletManager.activeWallets } answers { activeWallets }
        every { walletManager.activeWalletsFlow } returns activeWalletsFlow
        every { accountManager.account(account.id) } returns account

        service = ManageWalletsService(
            walletManager = walletManager,
            restoreSettingsService = restoreSettingsService,
            fullCoinsProvider = null,
            account = account,
            userDeletedWalletManager = userDeletedWalletManager,
            rescanMoneroUseCase = rescanMoneroUseCase,
            accountManager = accountManager,
        )
    }

    @After
    fun tearDown() {
        service.clear()
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun enable_zcashRestoredWithoutActiveZcash_requestsBirthdayHeight() = runTest(dispatcher) {
        val unifiedToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
        }

        service.enable(unifiedToken)
        advanceUntilIdle()

        verify {
            restoreSettingsService.approveSettings(
                token = unifiedToken,
                account = account,
                forceRequest = true,
                initialConfig = null
            )
        }
    }

    @Test
    fun disable_lastWalletOfChain_resetsOfflineModeAfterDeletion() = runTest(dispatcher) {
        val token = bitcoinToken()
        activeWallets = listOf(wallet(token))

        service.disable(token)
        advanceUntilIdle()

        coVerifyOrder {
            walletManager.deleteByTokenQueryIds(account.id, setOf(token.tokenQuery.id))
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Bitcoin)
        }
    }

    private fun bitcoinToken() = Token(
        coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
        blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
        type = TokenType.Derived(TokenType.Derivation.Bip84),
        decimals = 8
    )

    private fun wallet(token: Token) = mockk<Wallet> {
        every { this@mockk.token } returns token
    }

    @Test
    fun enable_trezorMoneroWithApprovedHeight_appliesHardwareRescan() = runTest(dispatcher) {
        val token = moneroToken()
        val wallet = mockk<Wallet> {
            every { this@mockk.token } returns token
        }
        val settings = RestoreSettings().apply {
            birthdayHeight = MONERO_RESTORE_HEIGHT
        }
        coEvery { hardwarePublicKeyProvider(account, token) } returns null
        every { walletFactory.create(token, account, null) } returns wallet
        advanceUntilIdle()

        approveSettingsSubject.onNext(
            RestoreSettingsService.TokenWithSettings(token, settings)
        )
        advanceUntilIdle()

        verify(timeout = 2_000) {
            walletFactory.create(token, account, null)
        }
        coVerifyOrder {
            rescanMoneroUseCase(account, MONERO_RESTORE_HEIGHT)
            walletFactory.create(token, account, null)
        }
    }

    @Test
    fun enable_trezorMoneroWithRejectedHeight_doesNotCreateWallet() = runTest(dispatcher) {
        val token = moneroToken()

        service.enable(token)
        advanceUntilIdle()
        rejectSettingsSubject.onNext(token)
        advanceUntilIdle()

        verify(exactly = 1) {
            restoreSettingsService.approveSettings(
                token = token,
                account = account,
                forceRequest = true,
                initialConfig = null,
            )
        }
        coVerify(exactly = 0) { rescanMoneroUseCase(any(), any()) }
        verify(exactly = 0) { walletFactory.create(any(), any(), any()) }
        coVerify(exactly = 0) { walletManager.saveSuspended(any()) }
    }

    private fun zcashTokens(): List<Token> {
        val coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC")
        val blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null)
        return TokenType.AddressSpecType.entries.map {
            Token(
                coin = coin,
                blockchain = blockchain,
                type = TokenType.AddressSpecTyped(it),
                decimals = 8
            )
        }
    }

    private fun moneroToken() = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
        type = TokenType.Native,
        decimals = 12,
    )

    private fun account() = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private companion object {
        const val MONERO_RESTORE_HEIGHT = 3_529_956L
    }

}

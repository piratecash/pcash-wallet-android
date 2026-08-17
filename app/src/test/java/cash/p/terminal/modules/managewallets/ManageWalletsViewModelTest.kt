package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManageWalletsViewModelTest : KoinTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val service = mockk<ManageWalletsService>(relaxed = true)
    private val accountManager = mockk<IAccountManager>()
    private val tangemScanUseCase = mockk<TangemScanUseCase>()
    private val hardwarePublicKeyStorage = mockk<HardwarePublicKeyStorage>(relaxed = true)
    private val hardwareWalletTokenPolicy = mockk<HardwareWalletTokenPolicy>()
    private val fetchTrezorPublicKeys = mockk<FetchTrezorPublicKeysUseCase>()
    private val addMonero = mockk<AddMoneroToTrezorAccountUseCase>()
    private val offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true)
    private val itemsFlow = MutableStateFlow<List<ManageWalletsService.Item>>(emptyList())
    private val restoreSettingsRejectedFlow = MutableSharedFlow<Token>()
    private val enabledTokensFlow = MutableSharedFlow<Token>()
    private val account = trezorAccount()
    private val token = moneroToken()

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IAccountManager> { accountManager }
                single { tangemScanUseCase }
                single { hardwarePublicKeyStorage }
                single<HardwareWalletTokenPolicy> { hardwareWalletTokenPolicy }
                single<FetchTrezorPublicKeysUseCase> { fetchTrezorPublicKeys }
                single { addMonero }
                single { offlineOperationGate }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(App.Companion)
        val app = mockk<CoreApp> {
            every { getString(any()) } returns "Add via Trezor"
        }
        every { App.instance } returns app
        every { accountManager.activeAccount } returns account
        every { accountManager.account(account.id) } returns account
        every { service.accountType } returns account.type
        every { service.itemsFlow } returns itemsFlow
        every { service.restoreSettingsRejectedFlow } returns restoreSettingsRejectedFlow
        every { service.enabledTokensFlow } returns enabledTokensFlow
        every { hardwareWalletTokenPolicy.isSupported(account, token) } returns true
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(account.id) } returns emptyList()
        coEvery { fetchTrezorPublicKeys(emptyList(), account.id) } returns emptyList()
        coEvery { addMonero.provision(account) } returns account
    }

    @After
    fun tearDown() {
        unmockkObject(App.Companion)
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun requestTrezorKeys_moneroPendingApproval_closesOnlyAfterEnabled() = runTest(dispatcher) {
        val viewModel = ManageWalletsViewModel(service, emptyList())
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

        assertFalse(viewModel.closeScreen)
        assertTrue(viewModel.showScanToAddButton)
        coVerify(exactly = 1) { addMonero.provision(account) }
        coVerify(exactly = 0) { addMonero(account) }
        verify(exactly = 1) { service.enable(token) }

        itemsFlow.value = listOf(
            ManageWalletsService.Item(token, enabled = true, hasInfo = false)
        )
        advanceUntilIdle()

        assertTrue(viewModel.closeScreen)
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun requestScanToAddTokens_scanAlreadyRunning_reusesActiveJob() = runTest(dispatcher) {
        val provisionedAccount = CompletableDeferred<Account>()
        coEvery { addMonero.provision(account) } coAnswers { provisionedAccount.await() }
        val viewModel = ManageWalletsViewModel(service, emptyList())
        viewModel.enable(token)

        val firstJob = viewModel.requestScanToAddTokens(closeAfterSuccess = false)
        val repeatedJob = viewModel.requestScanToAddTokens(closeAfterSuccess = true)

        assertSame(firstJob, repeatedJob)
        provisionedAccount.complete(account)
        firstJob.join()
        assertSame(
            firstJob,
            viewModel.requestScanToAddTokens(closeAfterSuccess = false),
        )
        coVerify(exactly = 1) { addMonero.provision(account) }
        verify(exactly = 1) { service.enable(token) }
    }

    @Test
    fun restoreSettingsRejected_moneroPendingApproval_clearsSelectionWithoutClosing() =
        runTest(dispatcher) {
            val viewModel = ManageWalletsViewModel(service, emptyList())
            viewModel.enable(token)
            viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

            restoreSettingsRejectedFlow.emit(token)
            advanceUntilIdle()

            assertFalse(viewModel.closeScreen)
            assertFalse(viewModel.showScanToAddButton)
        }

    @Test
    fun restoreSettingsRejected_moneroPendingWithoutAutoClose_clearsSelection() =
        runTest(dispatcher) {
            val viewModel = ManageWalletsViewModel(service, emptyList())
            viewModel.enable(token)
            viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

            restoreSettingsRejectedFlow.emit(token)
            advanceUntilIdle()

            assertFalse(viewModel.closeScreen)
            assertFalse(viewModel.showScanToAddButton)
        }

    private fun trezorAccount() = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun moneroToken() = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
        type = TokenType.Native,
        decimals = 12,
    )
}

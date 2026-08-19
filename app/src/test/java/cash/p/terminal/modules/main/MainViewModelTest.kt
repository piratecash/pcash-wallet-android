package cash.p.terminal.modules.main

import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.modules.softwareupdate.AppUpdateChecker
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.IPinComponent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Flowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val pinComponent = mockk<IPinComponent>(relaxed = true)
    private val backupManager = mockk<IBackupManager>(relaxed = true)
    private val termsManager = mockk<ITermsManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val releaseNotesManager = mockk<ReleaseNotesManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val wcSessionManager = mockk<WCSessionManager>(relaxed = true)
    private val wcManager = mockk<WCManager>(relaxed = true)
    private val logLoginAttemptUseCase = mockk<LogLoginAttemptUseCase>(relaxed = true)
    private val deeplinkParser = mockk<DeeplinkParser>(relaxed = true)
    private val appUpdateChecker = mockk<AppUpdateChecker>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)

    private var currentAccounts: List<Account> = emptyList()
    private var accountsEmpty = false
    private var allTermsAccepted = true
    private var storedMainTab: MainDestination? = null
    private val premiumTypesFlow = MutableStateFlow<Map<String, PremiumType>>(emptyMap())
    private val marketsTabEnabledFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        every { pinComponent.isLockedFlow } returns MutableStateFlow(false)
        every { pinComponent.pinSetFlowable } returns Flowable.empty()
        every { pinComponent.isPinSet } returns true
        every { localStorage.marketsTabEnabledFlow } returns marketsTabEnabledFlow
        every { localStorage.mainTab } answers { storedMainTab }
        every { localStorage.mainTab = any() } answers { storedMainTab = firstArg() }
        every { localStorage.isSystemPinRequired } returns true
        every { termsManager.termsAcceptedSignalFlow } returns emptyFlow()
        every { termsManager.allTermsAccepted } answers { allTermsAccepted }
        every { backupManager.allBackedUpFlow } returns emptyFlow()
        every { backupManager.allBackedUp } returns true
        every { wcSessionManager.pendingRequestCountFlow } returns MutableStateFlow(0)
        every { accountManager.accountsFlow } returns emptyFlow()
        every { accountManager.activeAccountStateFlow } returns emptyFlow()
        every { accountManager.hasNonStandardAccount } returns false
        every { accountManager.isAccountsEmpty } answers { accountsEmpty }
        every { accountManager.accounts } answers { currentAccounts }
        coEvery { logLoginAttemptUseCase.selfieEnabledAndHasProblem() } returns false
        every { appUpdateChecker.updateAvailable } returns MutableStateFlow(false)
        every { checkPremiumUseCase.premiumTypesFlow } returns premiumTypesFlow

        startKoin {
            modules(
                module {
                    single { appUpdateChecker }
                    single { checkPremiumUseCase }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun premiumTypesFlow_seedsWalletSwitchTypes() = runTest(dispatcher) {
        premiumTypesFlow.value = mapOf("a" to PremiumType.PIRATE)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mapOf("a" to PremiumType.PIRATE), viewModel.uiState.walletSwitchPremiumTypes)
    }

    @Test
    fun premiumTypesFlow_updatesWalletSwitchTypesOnEmission() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(emptyMap(), viewModel.uiState.walletSwitchPremiumTypes)

        // Background re-scan completes and the flow emits: the sheet's badge map updates.
        premiumTypesFlow.value = mapOf("b" to PremiumType.COSA)
        advanceUntilIdle()

        assertEquals(mapOf("b" to PremiumType.COSA), viewModel.uiState.walletSwitchPremiumTypes)
    }

    @Test
    fun navigationItems_marketsEnabledAndDisabled_updatesOrder() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MainDestination.entries.toList(), viewModel.uiState.mainNavItems.map { it.mainNavItem })

        marketsTabEnabledFlow.value = false
        advanceUntilIdle()

        assertEquals(
            listOf(MainDestination.Balance, MainDestination.Transactions, MainDestination.Settings),
            viewModel.uiState.mainNavItems.map { it.mainNavItem },
        )
    }

    @Test
    fun navigationItems_accountsEmpty_disablesTransactions() = runTest(dispatcher) {
        accountsEmpty = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            false,
            viewModel.uiState.mainNavItems.first { it.mainNavItem == MainDestination.Transactions }.enabled
        )
    }

    @Test
    fun navigationItems_termsNotAccepted_showsSettingsBadge() = runTest(dispatcher) {
        allTermsAccepted = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            MainModule.BadgeType.BadgeDot,
            viewModel.uiState.mainNavItems.first { it.mainNavItem == MainDestination.Settings }.badge,
        )
    }

    @Test
    fun onSelect_destinationPersistsSelection() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onSelect(MainDestination.Market)
        advanceUntilIdle()

        assertEquals(MainDestination.Market, storedMainTab)
        assertEquals(MainDestination.Market, viewModel.uiState.mainNavItems.first { it.selected }.mainNavItem)
    }

    private fun createViewModel() = MainViewModel(
        pinComponent = pinComponent,
        backupManager = backupManager,
        termsManager = termsManager,
        accountManager = accountManager,
        releaseNotesManager = releaseNotesManager,
        localStorage = localStorage,
        wcSessionManager = wcSessionManager,
        wcManager = wcManager,
        logLoginAttemptUseCase = logLoginAttemptUseCase,
        deeplinkParser = deeplinkParser
    )
}

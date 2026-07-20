package cash.p.terminal.modules.manageaccounts

import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAccountsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)

    private val accountsFlow = MutableSharedFlow<List<Account>>(extraBufferCapacity = 10)
    private val activeStateFlow = MutableSharedFlow<ActiveAccountState>(extraBufferCapacity = 10)
    private val premiumTypesFlow = MutableStateFlow<Map<String, PremiumType>>(emptyMap())

    private var currentAccounts: List<Account> = emptyList()
    private var currentActive: Account? = null

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { accountManager.accountsFlow } returns accountsFlow
        every { accountManager.activeAccountStateFlow } returns activeStateFlow
        every { accountManager.accounts } answers { currentAccounts }
        every { accountManager.activeAccount } answers { currentActive }
        every { checkPremiumUseCase.premiumTypesFlow } returns premiumTypesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_variousAccounts_groupsByPremiumAndKind() = runTest(dispatcher) {
        val regular = mnemonic("r", "Regular")
        val premiumRegular = mnemonic("p", "Premium")
        val watchAcc = watch("w", "Watch")
        val hardwareAcc = hardware("h", "Hardware")
        currentAccounts = listOf(regular, premiumRegular, watchAcc, hardwareAcc)
        currentActive = regular
        premiumTypesFlow.value = mapOf("p" to PremiumType.PIRATE)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("p"), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(listOf("r"), viewModel.regularAccountsState?.map { it.accountId })
        assertEquals(listOf("w"), viewModel.watchAccountsState?.map { it.accountId })
        assertEquals(listOf("h"), viewModel.hardwareAccountsState?.map { it.accountId })
        assertEquals(PremiumType.PIRATE, viewModel.premiumAccountsState?.first()?.premiumType)
    }

    @Test
    fun init_trialPremium_placedInPremiumGroup() = runTest(dispatcher) {
        val account = mnemonic("t", "Trial")
        currentAccounts = listOf(account)
        currentActive = account
        premiumTypesFlow.value = mapOf("t" to PremiumType.TRIAL)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("t"), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(emptyList(), viewModel.regularAccountsState?.map { it.accountId })
    }

    @Test
    fun init_hardwarePremium_placedInPremiumGroupNotHardware() = runTest(dispatcher) {
        val hardwarePremium = hardware("hp", "Hardware Premium")
        currentAccounts = listOf(hardwarePremium)
        currentActive = hardwarePremium
        premiumTypesFlow.value = mapOf("hp" to PremiumType.COSA)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("hp"), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(emptyList(), viewModel.hardwareAccountsState?.map { it.accountId })
    }

    @Test
    fun init_watchAccountReportedPremium_staysInWatchGroup() = runTest(dispatcher) {
        val watchAcc = watch("w", "Watch")
        currentAccounts = listOf(watchAcc)
        currentActive = null
        // Defensive: even if the premium map wrongly claims a watch account is premium.
        premiumTypesFlow.value = mapOf("w" to PremiumType.PIRATE)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(listOf("w"), viewModel.watchAccountsState?.map { it.accountId })
    }

    @Test
    fun premiumFlowResolvesLater_promotesAccountFromNormalGroup() = runTest(dispatcher) {
        val account = mnemonic("a", "A")
        currentAccounts = listOf(account)
        currentActive = account
        // Premium not resolved yet (cold cache): the display flow starts empty.
        premiumTypesFlow.value = emptyMap()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(listOf("a"), viewModel.regularAccountsState?.map { it.accountId })

        // Background re-scan completes and the flow emits: the account is promoted into Premium Active.
        premiumTypesFlow.value = mapOf("a" to PremiumType.PIRATE)
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(emptyList(), viewModel.regularAccountsState?.map { it.accountId })
    }

    @Test
    fun partialAccountsEmission_rendersFullAccountManagerSnapshot() = runTest(dispatcher) {
        val a = mnemonic("a", "A")
        val b = mnemonic("b", "B")
        currentAccounts = listOf(a, b)
        currentActive = a
        premiumTypesFlow.value = emptyMap()

        val viewModel = createViewModel()
        advanceUntilIdle()

        // A partial emission (only one account) must not drop the rest: the VM reads the full cache.
        accountsFlow.emit(listOf(a))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.regularAccountsState?.map { it.accountId })
    }

    @Test
    fun accountAddedLater_picksUpBadgeFromFlow() = runTest(dispatcher) {
        val a = mnemonic("a", "A")
        val b = mnemonic("b", "B")
        currentAccounts = listOf(a)
        currentActive = a
        premiumTypesFlow.value = mapOf("a" to PremiumType.PIRATE, "b" to PremiumType.COSA)

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.premiumAccountsState?.map { it.accountId })

        // A second account appears; it picks up its badge from the same premium flow.
        currentAccounts = listOf(a, b)
        accountsFlow.emit(listOf(a, b))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.premiumAccountsState?.map { it.accountId })
        assertEquals(
            listOf(PremiumType.PIRATE, PremiumType.COSA),
            viewModel.premiumAccountsState?.map { it.premiumType }
        )
    }

    private fun createViewModel() =
        ManageAccountsViewModel(accountManager, checkPremiumUseCase, ManageAccountsModule.Mode.Manage)

    private fun mnemonic(id: String, name: String) = Account(
        id = id,
        name = name,
        type = AccountType.Mnemonic(words = List(12) { "abandon" }, passphrase = ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true
    )

    private fun watch(id: String, name: String) = Account(
        id = id,
        name = name,
        type = AccountType.EvmAddress(address = "0x$id"),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private fun hardware(id: String, name: String) = Account(
        id = id,
        name = name,
        type = AccountType.HardwareCard(
            cardId = id,
            backupCardsCount = 0,
            walletPublicKey = "pub-$id",
            signedHashes = 0
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )
}

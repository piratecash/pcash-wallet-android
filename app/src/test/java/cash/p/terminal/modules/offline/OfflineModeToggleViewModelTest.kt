package cash.p.terminal.modules.offline

import cash.p.terminal.R
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.TransitionResult
import cash.p.terminal.entities.OfflineBlockchain
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeToggleViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)

    private val account = Account(
        id = "account-1",
        name = "Test Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        stopKoin()
        startKoin { modules(module { single { numberFormatter } }) }
        mockkObject(Translator)

        every { Translator.getString(R.string.offline_mode_settings_description, *anyVararg()) } answers {
            val params = secondArg<Array<out Any>>()
            "${params[0]}|${params[1]}"
        }
        every { Translator.getString(R.string.offline_mode_settings_assets_more, *anyVararg()) } answers {
            "+${secondArg<Array<out Any>>()[0]}"
        }
        every { Translator.getString(R.string.offline_mode_go_online_failed) } returns "go-online-failed"
        every { Translator.getString(R.string.offline_mode_go_offline_failed) } returns "go-offline-failed"
        every { Translator.getString(R.string.offline_mode_state_degraded) } returns "state-degraded"

        every { offlineModeManager.stateFlow } returns MutableStateFlow(emptyMap())
        every { walletManager.activeWallets } returns emptyList()
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun wallet(code: String, addressSpecType: TokenType.AddressSpecType, acc: Account = account): Wallet {
        val token = Token(
            coin = Coin(uid = code, name = code, code = code),
            blockchain = Blockchain(type = BlockchainType.Ethereum, name = "Ethereum", eip3091url = null),
            type = TokenType.AddressSpecTyped(addressSpecType),
            decimals = 8,
        )
        return checkNotNull(WalletFactory(mockk(relaxed = true)).create(token, acc, null))
    }

    private fun createViewModel(wallet: Wallet) = OfflineModeToggleViewModel(
        wallet = wallet,
        offlineModeManager = offlineModeManager,
        offlineModeUseCase = offlineModeUseCase,
        walletManager = walletManager,
        adapterManager = adapterManager,
    )

    private fun stubBalances(members: List<Wallet>) {
        every { walletManager.activeWallets } returns members
        every { adapterManager.getBalanceAdapterForWallet(any()) } returns mockk {
            every { balanceData } returns BalanceData(available = BigDecimal.ONE)
        }
    }

    @Test
    fun description_oneAsset_listsTicker() {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        stubBalances(listOf(w))

        val viewModel = createViewModel(w)

        assertEquals("Ethereum|ETH", viewModel.uiState.description)
    }

    @Test
    fun description_twoAssets_listsBothTickers() {
        val w1 = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val w2 = wallet("USDT", TokenType.AddressSpecType.Shielded)
        stubBalances(listOf(w1, w2))

        val viewModel = createViewModel(w1)

        assertEquals("Ethereum|ETH, USDT", viewModel.uiState.description)
    }

    @Test
    fun description_fiveAssetsSameBadge_showsBadgeInMore() {
        val members = listOf(
            wallet("A", TokenType.AddressSpecType.Transparent),
            wallet("B", TokenType.AddressSpecType.Transparent),
            wallet("C", TokenType.AddressSpecType.Unified),
            wallet("D", TokenType.AddressSpecType.Unified),
            wallet("E", TokenType.AddressSpecType.Unified),
        )
        stubBalances(members)

        val viewModel = createViewModel(members[0])

        assertEquals("Ethereum|A, B, +UNIFIED", viewModel.uiState.description)
    }

    @Test
    fun description_fiveAssetsMixedBadge_showsCountInMore() {
        val members = listOf(
            wallet("A", TokenType.AddressSpecType.Transparent),
            wallet("B", TokenType.AddressSpecType.Transparent),
            wallet("C", TokenType.AddressSpecType.Unified),
            wallet("D", TokenType.AddressSpecType.Shielded),
            wallet("E", TokenType.AddressSpecType.Unified),
        )
        stubBalances(members)

        val viewModel = createViewModel(members[0])

        assertEquals("Ethereum|A, B, +3", viewModel.uiState.description)
    }

    @Test
    fun confirmationRequired_singleAsset_isFalse() {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        stubBalances(listOf(w))

        val viewModel = createViewModel(w)

        assertFalse(viewModel.uiState.confirmationRequired)
    }

    @Test
    fun confirmationRequired_twoAssets_isTrue() {
        val w1 = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val w2 = wallet("USDT", TokenType.AddressSpecType.Shielded)
        stubBalances(listOf(w1, w2))

        val viewModel = createViewModel(w1)

        assertTrue(viewModel.uiState.confirmationRequired)
    }

    @Test
    fun confirmOffline_success_closesSheetWithoutError() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } returns TransitionResult.Success

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()

        assertTrue(viewModel.uiState.closeSheet)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun confirmOffline_failed_setsErrorWithoutClosingSheet() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } returns TransitionResult.Failed(RuntimeException("boom"))

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()

        assertEquals("go-offline-failed", viewModel.uiState.error)
        assertFalse(viewModel.uiState.closeSheet)
    }

    @Test
    fun confirmOffline_whileInProgress_showsOfflineOptimistically() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val gate = CompletableDeferred<Unit>()
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } coAnswers {
            gate.await()
            TransitionResult.Success
        }

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()

        assertTrue(viewModel.uiState.offline)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun confirmOffline_failed_revertsOptimisticOffline() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } returns TransitionResult.Failed(RuntimeException("boom"))

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()

        assertFalse(viewModel.uiState.offline)
    }

    @Test
    fun goOnline_degraded_revertsOptimisticOffline() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val key = OfflineKey(account.id, BlockchainType.Ethereum)
        every { offlineModeManager.stateFlow } returns MutableStateFlow(
            mapOf(
                key to OfflineBlockchain(
                    account.id, BlockchainType.Ethereum, offline = true, enabledAt = null, lastSyncedAt = null
                )
            )
        )
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, false)
        } returns TransitionResult.Degraded(emptyList())

        val viewModel = createViewModel(w)
        viewModel.goOnline()

        assertTrue(viewModel.uiState.offline)
    }

    @Test
    fun goOnline_failed_setsErrorForTheOnlineDirection() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, false)
        } returns TransitionResult.Failed(RuntimeException("boom"))

        val viewModel = createViewModel(w)
        viewModel.goOnline()

        assertEquals("go-online-failed", viewModel.uiState.error)
    }

    @Test
    fun confirmOffline_degraded_setsError() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } returns TransitionResult.Degraded(emptyList())

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()

        assertEquals("state-degraded", viewModel.uiState.error)
        assertFalse(viewModel.uiState.closeSheet)
    }

    @Test
    fun offline_followsStateFlow_updatesWhenChanged() {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val key = OfflineKey(account.id, BlockchainType.Ethereum)
        val flow = MutableStateFlow<Map<OfflineKey, OfflineBlockchain>>(emptyMap())
        every { offlineModeManager.stateFlow } returns flow

        val viewModel = createViewModel(w)
        assertFalse(viewModel.uiState.offline)

        flow.value = mapOf(
            key to OfflineBlockchain(
                account.id, BlockchainType.Ethereum, offline = true, enabledAt = null, lastSyncedAt = null
            )
        )

        assertTrue(viewModel.uiState.offline)
    }

    @Test
    fun goOnline_callsUseCaseWithOfflineFalse() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        coEvery { offlineModeUseCase.setChainOffline(any(), any(), any()) } returns TransitionResult.Success

        val viewModel = createViewModel(w)
        viewModel.goOnline()

        coVerify { offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, false) }
    }

    @Test
    fun confirmOffline_calledTwiceWhileInProgress_runsTransitionOnce() = runTest(dispatcher) {
        val w = wallet("ETH", TokenType.AddressSpecType.Transparent)
        val gate = CompletableDeferred<Unit>()
        coEvery {
            offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true)
        } coAnswers {
            gate.await()
            TransitionResult.Success
        }

        val viewModel = createViewModel(w)
        viewModel.confirmOffline()
        assertTrue(viewModel.uiState.inProgress)
        viewModel.confirmOffline()

        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineModeUseCase.setChainOffline(account, BlockchainType.Ethereum, true) }
        assertFalse(viewModel.uiState.inProgress)
        assertTrue(viewModel.uiState.closeSheet)
    }
}

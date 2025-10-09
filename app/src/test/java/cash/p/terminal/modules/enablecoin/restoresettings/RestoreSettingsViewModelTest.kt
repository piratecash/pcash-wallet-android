package cash.p.terminal.modules.enablecoin.restoresettings

import cash.p.terminal.core.managers.AccountCleaner
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreSettingsViewModelTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<RestoreSettingsService>(relaxed = true)
    private val accountCleaner = mockk<AccountCleaner>(relaxed = true)
    private lateinit var requestSubject: PublishSubject<RestoreSettingsService.Request>

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { accountCleaner }
            }
        )
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)

        requestSubject = PublishSubject.create()
        every { service.requestObservable } returns requestSubject
        coEvery { service.enter(any(), any()) } returns false
        justRun { service.cancel(any()) }
        coEvery { accountCleaner.clearWalletForCurrentAccount(any()) } returns Unit
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `handle request exposes token`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init

        val token = token(BlockchainType.Zcash)
        val initialConfig = TokenConfig("123", false)

        requestSubject.onNext(
            RestoreSettingsService.Request(
                token = token,
                requestType = RestoreSettingsService.RequestType.BirthdayHeight,
                initialConfig = initialConfig
            )
        )
        advanceUntilIdle()

        assertEquals(token, viewModel.openTokenConfigure)
        assertEquals(initialConfig, viewModel.consumeInitialConfig())
        assertNull(viewModel.consumeInitialConfig())
    }

    @Test
    fun `tokenConfigureOpened clears state`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init
        val token = token(BlockchainType.Zcash)

        requestSubject.onNext(
            RestoreSettingsService.Request(
                token = token,
                requestType = RestoreSettingsService.RequestType.BirthdayHeight,
                initialConfig = null
            )
        )
        advanceUntilIdle()

        viewModel.tokenConfigureOpened()

        assertNull(viewModel.openTokenConfigure)
    }

    @Test
    fun `onCancelEnterBirthdayHeight delegates to service`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init
        val token = token(BlockchainType.Zcash)

        requestSubject.onNext(
            RestoreSettingsService.Request(
                token = token,
                requestType = RestoreSettingsService.RequestType.BirthdayHeight,
                initialConfig = null
            )
        )
        advanceUntilIdle()

        viewModel.onCancelEnterBirthdayHeight()

        verify(exactly = 1) { service.cancel(token) }
    }

    @Test
    fun `onEnter clears wallet before service when height changed`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init

        val token = token(BlockchainType.Zcash)
        val initialConfig = TokenConfig("100", false)

        requestSubject.onNext(
            RestoreSettingsService.Request(
                token = token,
                requestType = RestoreSettingsService.RequestType.BirthdayHeight,
                initialConfig = initialConfig
            )
        )
        advanceUntilIdle()

        val newConfig = TokenConfig("200", false)
        viewModel.onEnter(newConfig)
        advanceUntilIdle()

        coVerifyOrder {
            accountCleaner.clearWalletForCurrentAccount(token.blockchainType)
            service.enter(newConfig, token)
        }
    }

    @Test
    fun `onEnter skips clearing when height unchanged`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init

        val token = token(BlockchainType.Zcash)
        val initialConfig = TokenConfig("100", false)

        requestSubject.onNext(
            RestoreSettingsService.Request(
                token = token,
                requestType = RestoreSettingsService.RequestType.BirthdayHeight,
                initialConfig = initialConfig
            )
        )
        advanceUntilIdle()

        viewModel.onEnter(initialConfig)
        advanceUntilIdle()

        verify(exactly = 1) { service.enter(initialConfig, token) }
        coVerify(exactly = 0) { accountCleaner.clearWalletForCurrentAccount(any()) }
    }

    @Test
    fun `onEnter does nothing without request`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle() // wait for init

        viewModel.onEnter(TokenConfig("123", false))
        advanceUntilIdle()

        verify(exactly = 0) { service.enter(any(), any()) }
        coVerify(exactly = 0) { accountCleaner.clearWalletForCurrentAccount(any()) }
        verify(exactly = 0) { service.cancel(any()) }
    }

    @Test
    fun `onCleared clears every clearable`() {
        val clearable1 = mockk<Clearable>(relaxed = true)
        val clearable2 = mockk<Clearable>(relaxed = true)

        val viewModel = createViewModel(listOf(clearable1, clearable2))

        viewModel.invokeOnClearedForTest()

        verify(exactly = 1) { clearable1.clear() }
        verify(exactly = 1) { clearable2.clear() }
    }

    private fun createViewModel(clearables: List<Clearable> = emptyList()): RestoreSettingsViewModel =
        RestoreSettingsViewModel(service, clearables)

    private fun token(blockchainType: BlockchainType): Token {
        return Token(
            coin = Coin(
                uid = "uid-${blockchainType.uid}",
                name = "Coin-${blockchainType.uid}",
                code = blockchainType.uid.uppercase()
            ),
            blockchain = Blockchain(
                type = blockchainType,
                name = blockchainType.uid,
                eip3091url = null
            ),
            type = TokenType.Native,
            decimals = 8
        )
    }

    private fun RestoreSettingsViewModel.invokeOnClearedForTest() {
        RestoreSettingsViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(this@invokeOnClearedForTest)
        }
    }
}

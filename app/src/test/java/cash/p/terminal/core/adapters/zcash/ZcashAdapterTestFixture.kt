package cash.p.terminal.core.adapters.zcash

import android.content.Context
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.block.processor.CompactBlockProcessor
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.PercentDecimal
import cash.z.ecc.android.sdk.model.TransactionOverview
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Shared harness for [ZcashAdapter] tests: a real adapter on top of a mockk synchronizer.
 *
 * Every adapter coroutine — recovery, status jobs, subscriptions — runs on the single
 * [StandardTestDispatcher] the subclass passes to `runTest`, so waits are driven by the
 * virtual-time scheduler instead of real timeouts.
 *
 * A subclass only adds what is specific to its scenarios: synchronizer stubs in
 * [stubSynchronizer] and companion-object stubs in [stubSynchronizerCompanion].
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ZcashAdapterTestFixture {

    protected val dispatcher = StandardTestDispatcher()

    // Separate from the `runTest` scope on purpose: the adapter's subscriber collectors are
    // parented to synchronizer.coroutineScope (ZcashAdapter.subscribe()) and never complete on
    // their own. If they were children of the `runTest` scope, `runTest` would hang waiting for
    // them. They live in `appScope` instead, cancelled explicitly in tearDownFixture().
    protected val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    protected val context = mockk<Context>(relaxed = true)
    protected val wallet = mockk<Wallet>(relaxed = true)
    protected val localStorage = mockk<ILocalStorage>(relaxed = true)
    protected val backgroundManager = mockk<BackgroundManager>(relaxed = true)
    protected val singleUseAddressManager = mockk<ZcashSingleUseAddressManager>(relaxed = true)
    protected val clearZCashWalletDataUseCase = mockk<ClearZCashWalletDataUseCase>(relaxed = true)
    protected val backgroundKeepAliveManager = mockk<BackgroundKeepAliveManager>(relaxed = true)
    protected val restoreSettings = RestoreSettings().apply { birthdayHeight = 2000000L }

    protected val statusFlow = MutableStateFlow(Synchronizer.Status.SYNCING)
    protected val progressFlow = MutableStateFlow(PercentDecimal.ZERO_PERCENT)
    protected val walletBalancesFlow = MutableStateFlow<Map<AccountUuid, AccountBalance>?>(null)
    protected val processorInfoFlow = MutableStateFlow(
        CompactBlockProcessor.ProcessorInfo(null, null, null)
    )
    protected val allTransactionsFlow = MutableStateFlow<List<TransactionOverview>>(emptyList())

    protected val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })

    protected lateinit var mockSynchronizer: SdkSynchronizer
    protected lateinit var adapter: ZcashAdapter

    @Before
    fun setUpFixture() {
        Dispatchers.setMain(dispatcher)
        CoreApp.instance = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { clearZCashWalletDataUseCase }
                single { backgroundKeepAliveManager }
            })
        }

        val accountType = mockk<AccountType.Mnemonic>(relaxed = true) {
            every { seed } returns ByteArray(64) { it.toByte() }
        }
        val account = mockk<Account>(relaxed = true) {
            every { id } returns "test-account-id"
            every { name } returns "Test"
            every { type } returns accountType
            every { origin } returns AccountOrigin.Created
        }
        every { wallet.account } returns account
        every { localStorage.zcashAccountIds } returns setOf("test-account-id")
        every { localStorage.torEnabled } returns false
        every { backgroundManager.stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
        every {
            clearZCashWalletDataUseCase.getValidAliasFromAccountId(any(), any())
        } returns "zcash_test"

        mockkObject(BlockHeight.Companion)
        coEvery { BlockHeight.ofLatestCheckpoint(any(), any()) } returns BlockHeight.new(2500000L)

        mockSynchronizer = mockk<SdkSynchronizer>(relaxed = true) {
            every { status } returns statusFlow
            every { progress } returns progressFlow
            every { walletBalances } returns walletBalancesFlow
            every { processorInfo } returns processorInfoFlow
            every { allTransactions } returns allTransactionsFlow
            every { coroutineScope } returns appScope
            every { latestHeight } returns null
        }
        stubSynchronizer()

        mockkObject(Synchronizer)
        every {
            Synchronizer.newBlocking(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } returns mockSynchronizer
        stubSynchronizerCompanion()
    }

    @After
    fun tearDownFixture() {
        if (::adapter.isInitialized) {
            adapter.stop()
        }
        appScope.cancel()
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    /** Stubs on [mockSynchronizer] itself that are specific to the subclass's scenarios. */
    protected open fun stubSynchronizer() = Unit

    /** Stubs on the [Synchronizer] companion that are specific to the subclass's scenarios. */
    protected open fun stubSynchronizerCompanion() = Unit

    protected fun createAdapter(addressSpecTyped: AddressSpecType? = null) = ZcashAdapter(
        context = context,
        wallet = wallet,
        restoreSettings = restoreSettings,
        addressSpecTyped = addressSpecTyped,
        localStorage = localStorage,
        backgroundManager = backgroundManager,
        singleUseAddressManager = singleUseAddressManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, appScope),
    )
}

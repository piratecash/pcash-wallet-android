package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Coverage for the offline-mode network gate in TonKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 *
 * TonKit's start()/refresh() are suspend functions, unlike Solana/Tron, so this uses
 * coEvery/coVerify instead of the plain every/verify used by the non-suspend kits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TonKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Ton",
        type = AccountType.TonAddress("UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockTonKit = mockk<TonKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: TonKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = TonKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private val backgroundStateFlow =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.EnterForeground)

    private fun createManager(): TonKitManager {
        val manager = TonKitManager(
            backgroundManager = mockk<BackgroundManager>(relaxed = true) {
                every { stateFlow } returns backgroundStateFlow
            },
            hardwarePublicKeyStorage = mockk(relaxed = true),
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        )
        setField(manager, "tonKitWrapper", TonKitWrapper(mockTonKit, mockk<TonWallet>(relaxed = true)))
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: TonKitManager, name: String, value: Any?) {
        TonKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: TonKitManager): Int =
        (TonKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Ton)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockTonKit.start() }
        coVerify(exactly = 0) { mockTonKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Ton)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockTonKit.start() }
        coVerify(exactly = 1) { mockTonKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun start_wrapperReplacedByAccountSwitch_keepsDrivingItsOwnKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Ton)) } returns false
        val manager = createManager()
        val startJob = launch {
            invokeStart(manager, account, TonKitWrapper(mockTonKit, mockk<TonWallet>(relaxed = true)))
        }
        advanceUntilIdle()

        val nextKit = mockk<TonKit>(relaxed = true)
        setField(manager, "tonKitWrapper", TonKitWrapper(nextKit, mockk<TonWallet>(relaxed = true)))
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        verify(exactly = 1) { mockTonKit.stop() }
        verify(exactly = 0) { nextKit.stop() }
        startJob.cancel()
    }

    @Test
    fun stop_callerCancelledDuringTeardown_stopsKitAndDoesNotResume() = testScope.runTest {
        val manager = createManager()
        // The lifecycle job hangs in NonCancellable cleanup, so stop() is parked inside cancelAndJoin
        // when the caller is cancelled — the interleaving the teardown has to survive.
        val cleanupGate = CompletableDeferred<Unit>()
        setField(manager, "job", backgroundScope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanupGate.await() }
            }
        })

        var resumedAfterStop = false
        val caller = launch {
            invokeStop(manager)
            resumedAfterStop = true
        }
        caller.cancel()
        advanceUntilIdle()

        assertNull(manager.tonKitWrapper)
        cleanupGate.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { mockTonKit.stop() }
        assertFalse(resumedAfterStop)
    }

    private suspend fun invokeStart(manager: TonKitManager, account: Account, wrapper: TonKitWrapper) {
        TonKitManager::class.declaredMemberFunctions.first { it.name == "start" }
            .apply { isAccessible = true }
            .callSuspend(manager, account, wrapper)
    }

    private suspend fun invokeStop(manager: TonKitManager) {
        TonKitManager::class.declaredMemberFunctions.first { it.name == "stop" }
            .apply { isAccessible = true }
            .callSuspend(manager)
    }
}

/**
 * Coverage for the offline-mode network gate in StellarKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 *
 * StellarKit's start()/refresh() are suspend functions, unlike Solana/Tron, so this uses
 * coEvery/coVerify instead of the plain every/verify used by the non-suspend kits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StellarKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Stellar",
        type = AccountType.StellarAddress("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockStellarKit = mockk<StellarKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: StellarKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = StellarKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private val backgroundStateFlow =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.EnterForeground)

    private fun createManager(): StellarKitManager {
        val manager = StellarKitManager(
            backgroundManager = mockk<BackgroundManager>(relaxed = true) {
                every { stateFlow } returns backgroundStateFlow
            },
            hardwarePublicKeyStorage = mockk(relaxed = true),
            trezorClient = mockk(relaxed = true),
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        )
        setField(manager, "stellarKitWrapper", StellarKitWrapper(mockStellarKit))
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: StellarKitManager, name: String, value: Any?) {
        StellarKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: StellarKitManager): Int =
        (StellarKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockStellarKit.start() }
        coVerify(exactly = 0) { mockStellarKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockStellarKit.start() }
        coVerify(exactly = 1) { mockStellarKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun start_wrapperReplacedByAccountSwitch_keepsDrivingItsOwnKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns false
        val manager = createManager()
        val startJob = launch { invokeStart(manager, account, StellarKitWrapper(mockStellarKit)) }
        advanceUntilIdle()

        val nextKit = mockk<StellarKit>(relaxed = true)
        setField(manager, "stellarKitWrapper", StellarKitWrapper(nextKit))
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        verify(exactly = 1) { mockStellarKit.stop() }
        verify(exactly = 0) { nextKit.stop() }
        startJob.cancel()
    }

    @Test
    fun stop_callerCancelledDuringTeardown_destroysKitAndDoesNotResume() = testScope.runTest {
        val manager = createManager()
        // The lifecycle job hangs in NonCancellable cleanup, so stop() is parked inside cancelAndJoin
        // when the caller is cancelled — the interleaving the teardown has to survive.
        val cleanupGate = CompletableDeferred<Unit>()
        setField(manager, "job", backgroundScope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanupGate.await() }
            }
        })

        var resumedAfterStop = false
        val caller = launch {
            invokeStop(manager)
            resumedAfterStop = true
        }
        caller.cancel()
        advanceUntilIdle()

        assertNull(manager.stellarKitWrapper)
        cleanupGate.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { mockStellarKit.destroy() }
        assertFalse(resumedAfterStop)
    }

    private suspend fun invokeStart(
        manager: StellarKitManager,
        account: Account,
        wrapper: StellarKitWrapper,
    ) {
        StellarKitManager::class.declaredMemberFunctions.first { it.name == "start" }
            .apply { isAccessible = true }
            .callSuspend(manager, account, wrapper)
    }

    private suspend fun invokeStop(manager: StellarKitManager) {
        StellarKitManager::class.declaredMemberFunctions.first { it.name == "stop" }
            .apply { isAccessible = true }
            .callSuspend(manager)
    }
}

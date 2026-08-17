package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.OfflineNetworkController
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.zcashTransparentWallet
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFailsWith

/**
 * [OfflineNetworkController] is mocked wholesale here: its per-family dispatch is covered by
 * OfflineNetworkControllerTest. This suite covers what [OfflineModeUseCase] alone owns - the
 * single-worker queue, the 3-state outcome/compensation algorithm, and how mode toggles,
 * account deletion and temporary-online all share that one queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val networkController = mockk<OfflineNetworkController>(relaxed = true)
    private val walletManager = mockk<IWalletManager>()

    private val account = zcashMnemonicAccount(ACCOUNT_ID)
    private val wallet = zcashTransparentWallet(account)
    private val key = OfflineKey(ACCOUNT_ID, BlockchainType.Zcash)

    private fun createUseCase(members: List<Wallet> = listOf(wallet)): OfflineModeUseCase {
        every { walletManager.activeWallets } returns members
        return OfflineModeUseCase(
            offlineModeManager,
            networkController,
            walletManager,
            // SupervisorJob mirrors the production applicationScope (DispatcherProvider.kt) so that one
            // member's failing async doesn't cascade-cancel its siblings or the worker loop, same as prod.
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher + SupervisorJob())),
        )
    }

    /** A second member of the same (account, Zcash) pair - a distinct wallet on the same chain. */
    private fun zcashShieldedWallet(): Wallet = checkNotNull(
        WalletFactory(mockk(relaxed = true)).create(
            token = Token(
                coin = Coin(uid = "zcash-shielded", name = "Zcash", code = "ZEC"),
                blockchain = Blockchain(type = BlockchainType.Zcash, name = "Zcash", eip3091url = null),
                type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
                decimals = 8,
            ),
            account = account,
            hardwarePublicKey = null,
        )
    )

    private fun ethereumWallet(tokenType: TokenType, uid: String, code: String): Wallet = checkNotNull(
        WalletFactory(mockk(relaxed = true)).create(
            token = Token(
                coin = Coin(uid = uid, name = code, code = code),
                blockchain = Blockchain(type = BlockchainType.Ethereum, name = "Ethereum", eip3091url = null),
                type = tokenType,
                decimals = 8,
            ),
            account = account,
            hardwarePublicKey = null,
        )
    )

    @Test
    fun setChainOffline_enable_pausesMemberAndPersists() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        val useCase = createUseCase()

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertEquals(TransitionResult.Success, result)
        coVerify(exactly = 1) { networkController.pause(wallet) }
        coVerify(exactly = 0) { networkController.resume(wallet) }
        coVerify(exactly = 1) { offlineModeManager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, true) }
    }

    @Test
    fun setChainOffline_disable_resumesMemberAndPersists() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns true
        val useCase = createUseCase()

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = false)

        assertEquals(TransitionResult.Success, result)
        coVerify(exactly = 1) { networkController.resume(wallet) }
        coVerify(exactly = 1) { offlineModeManager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, false) }
    }

    @Test
    fun setChainOffline_rapidToggle_convergesToLastRequestedState() = runTest(dispatcher) {
        var paused = false
        coEvery { networkController.pause(wallet) } answers { paused = true }
        coEvery { networkController.resume(wallet) } answers { paused = false }
        every { networkController.isOffline(wallet) } answers { paused }
        val useCase = createUseCase()

        val results = listOf(
            useCase.setChainOffline(account, BlockchainType.Zcash, true),
            useCase.setChainOffline(account, BlockchainType.Zcash, false),
            useCase.setChainOffline(account, BlockchainType.Zcash, true),
        )

        assertTrue(results.all { it == TransitionResult.Success })
        assertTrue(paused)
        coVerify(exactly = 2) { networkController.pause(wallet) }
        coVerify(exactly = 1) { networkController.resume(wallet) }
    }

    @Test
    fun setChainOffline_pauseThrows_returnsFailedWithoutDbWrite() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        coEvery { networkController.pause(wallet) } throws IOException("kit down")
        val useCase = createUseCase()

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertTrue(result is TransitionResult.Failed)
        coVerify(exactly = 0) { offlineModeManager.persistAndPublish(any(), any(), any()) }
    }

    @Test
    fun setChainOffline_persistAndPublishThrows_compensatesByResumingAppliedMember() = runTest(dispatcher) {
        var paused = false
        coEvery { networkController.pause(wallet) } answers { paused = true }
        coEvery { networkController.resume(wallet) } answers { paused = false }
        every { networkController.isOffline(wallet) } answers { paused }
        coEvery { offlineModeManager.persistAndPublish(any(), any(), any()) } throws IOException("disk full")
        val useCase = createUseCase()

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertTrue(result is TransitionResult.Failed)
        coVerify(exactly = 1) { networkController.pause(wallet) }
        coVerify(exactly = 1) { networkController.resume(wallet) }
        assertFalse(paused)
    }

    @Test
    fun setChainOffline_partialFailureAndCompensationAlsoFails_returnsDegradedWithoutDbWrite() = runTest(dispatcher) {
        val other = zcashShieldedWallet()
        var walletPaused = false
        coEvery { networkController.pause(wallet) } answers { walletPaused = true }
        every { networkController.isOffline(wallet) } answers { walletPaused }
        coEvery { networkController.resume(wallet) } throws IOException("resume also fails")
        every { networkController.isOffline(other) } returns false
        coEvery { networkController.pause(other) } throws IOException("kit down")
        val useCase = createUseCase(members = listOf(wallet, other))

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertTrue(result is TransitionResult.Degraded)
        coVerify(exactly = 1) { networkController.resume(wallet) }
        coVerify(exactly = 0) { offlineModeManager.persistAndPublish(any(), any(), any()) }
    }

    @Test
    fun setChainOffline_pauseTimesOut_memberStaysUnknownWithoutReverseOpOrDbWrite() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        coEvery { networkController.pause(wallet) } coAnswers { delay(LIFECYCLE_TIMEOUT_MS + 5_000) }
        val useCase = createUseCase()

        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertTrue(result is TransitionResult.Degraded)
        val outcome = (result as TransitionResult.Degraded).members.single()
        assertEquals(LifecycleOutcome.Unknown, outcome.outcome)
        coVerify(exactly = 0) { networkController.resume(wallet) }
        coVerify(exactly = 0) { offlineModeManager.persistAndPublish(any(), any(), any()) }
    }

    @Test
    fun setChainOffline_afterPriorTimeout_nextCommandAwaitsPendingAndConverges() = runTest(dispatcher) {
        var paused = false
        coEvery { networkController.pause(wallet) } coAnswers {
            delay(LIFECYCLE_TIMEOUT_MS + 5_000)
            paused = true
        }
        every { networkController.isOffline(wallet) } answers { paused }
        val useCase = createUseCase()

        val first = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)
        assertTrue(first is TransitionResult.Degraded)

        val second = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertEquals(TransitionResult.Success, second)
        // No duplicate kit op - converged via authoritative state instead of retrying the stale op.
        coVerify(exactly = 1) { networkController.pause(wallet) }
        coVerify(exactly = 1) { offlineModeManager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, true) }
    }

    @Test
    fun setChainOffline_pendingPauseThenOppositeRequest_degradesWithoutPersisting() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        coEvery { networkController.pause(wallet) } coAnswers { delay(LIFECYCLE_TIMEOUT_MS * 3) }
        val useCase = createUseCase()

        assertTrue(useCase.setChainOffline(account, BlockchainType.Zcash, offline = true) is TransitionResult.Degraded)
        val result = useCase.setChainOffline(account, BlockchainType.Zcash, offline = false)

        assertTrue(result is TransitionResult.Degraded)
        coVerify(exactly = 0) { networkController.resume(wallet) }
        coVerify(exactly = 0) { offlineModeManager.persistAndPublish(any(), any(), any()) }
    }

    @Test
    fun withTemporaryOnline_pendingMember_doesNotEnterOrRunBlock() = runTest(dispatcher) {
        val sibling = zcashShieldedWallet()
        every { networkController.isOffline(wallet) } returns false
        every { networkController.isOffline(sibling) } returns true
        coEvery { networkController.pause(wallet) } coAnswers { delay(LIFECYCLE_TIMEOUT_MS * 3) }
        val useCase = createUseCase(listOf(wallet, sibling))

        assertTrue(useCase.setChainOffline(account, BlockchainType.Zcash, offline = true) is TransitionResult.Degraded)
        var blockRan = false
        assertFailsWith<IllegalStateException> {
            useCase.withTemporaryOnline(account, BlockchainType.Zcash) { blockRan = true }
        }

        assertFalse(blockRan)
        coVerify(exactly = 0) { offlineModeManager.enterTemporaryOnline(any(), any()) }
        coVerify(exactly = 0) { networkController.resume(wallet) }
        coVerify(exactly = 0) { networkController.resume(sibling) }
    }

    @Test
    fun withTemporaryOnline_partialEntryTimeout_restoresBothOriginallyOfflineMembers() = runTest(dispatcher) {
        val sibling = zcashShieldedWallet()
        var siblingOffline = true
        every { networkController.isOffline(wallet) } returnsMany listOf(true, false, true)
        every { networkController.isOffline(sibling) } answers { siblingOffline }
        every { offlineModeManager.isNetworkPaused(key) } returns true
        coEvery { networkController.resume(sibling) } coAnswers {
            delay(LIFECYCLE_TIMEOUT_MS + 5_000)
            siblingOffline = false
        }
        coEvery { networkController.pause(sibling) } coAnswers { siblingOffline = true }
        val useCase = createUseCase(listOf(wallet, sibling))
        var blockRan = false

        assertFailsWith<IllegalStateException> {
            useCase.withTemporaryOnline(account, BlockchainType.Zcash) { blockRan = true }
        }

        assertFalse(blockRan)
        advanceUntilIdle()
        testScheduler.runCurrent()
        coVerify(exactly = 2) { offlineModeManager.exitTemporaryOnline(key, any()) }
        coVerifyOrder { offlineModeManager.exitTemporaryOnline(key, any()); networkController.pause(wallet) }
        assertTrue(siblingOffline)
    }

    @Test
    fun setChainOffline_sharedEvmMembers_pausesBothAdaptersBeforePersisting() = runTest(dispatcher) {
        val native = ethereumWallet(TokenType.Native, "eth", "ETH")
        val token = ethereumWallet(TokenType.Eip20("0xUSDC"), "usdc", "USDC")
        var sharedOffline = false
        every { networkController.isOffline(any()) } answers { sharedOffline }
        coEvery { networkController.pause(native) } coAnswers { sharedOffline = true }
        val useCase = createUseCase(listOf(native, token))

        val first = useCase.setChainOffline(account, BlockchainType.Ethereum, offline = true)
        val second = useCase.setChainOffline(account, BlockchainType.Ethereum, offline = true)

        assertEquals(TransitionResult.Success, first)
        assertEquals(TransitionResult.Success, second)
        coVerify(exactly = 1) { networkController.pause(native) }
        coVerify(exactly = 1) { networkController.pause(token) }
        coVerify(exactly = 2) { offlineModeManager.persistAndPublish(ACCOUNT_ID, BlockchainType.Ethereum, true) }
    }

    @Test
    fun setChainOffline_repeatedAtSameTarget_stillPersistsInsteadOfNoOp() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        val useCase = createUseCase()

        val first = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)
        every { networkController.isOffline(wallet) } returns true // now already at target
        val second = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertEquals(TransitionResult.Success, first)
        assertEquals(TransitionResult.Success, second)
        coVerify(exactly = 1) { networkController.pause(wallet) } // fast path on the repeat, no duplicate kit op
        coVerify(exactly = 2) { offlineModeManager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, true) }
    }

    @Test
    fun setChainOffline_unexpectedExceptionInCommand_doesNotHangQueue() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        coEvery { offlineModeManager.beginTransition(any(), any()) } throws IllegalStateException("boom") andThen Unit
        val useCase = createUseCase()

        val first = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)
        val second = useCase.setChainOffline(account, BlockchainType.Zcash, offline = true)

        assertTrue(first is TransitionResult.Failed)
        assertEquals(TransitionResult.Success, second)
    }

    @Test
    fun forgetAccounts_delegatesToManagerThroughQueue() = runTest(dispatcher) {
        val useCase = createUseCase()

        useCase.forgetAccounts(listOf(ACCOUNT_ID))

        coVerify(exactly = 1) { offlineModeManager.forgetAccounts(listOf(ACCOUNT_ID)) }
    }

    @Test
    fun resetIfBlockchainRemoved_lastWalletOfChainGone_resetsChain() = runTest(dispatcher) {
        coEvery { walletManager.getWallets(account) } returns emptyList()
        val useCase = createUseCase()

        useCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineModeManager.resetChain(ACCOUNT_ID, BlockchainType.Zcash) }
    }

    @Test
    fun resetIfBlockchainRemoved_anotherWalletOfChainRemains_keepsMode() = runTest(dispatcher) {
        coEvery { walletManager.getWallets(account) } returns listOf(zcashShieldedWallet())
        val useCase = createUseCase()

        useCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineModeManager.resetChain(any(), any()) }
    }

    /** The chain is gone from storage, but a paused adapter for it is still live: reset must free it. */
    @Test
    fun resetIfBlockchainRemoved_pausedMemberStillLive_resumesIt() = runTest(dispatcher) {
        coEvery { walletManager.getWallets(account) } returns emptyList()
        every { networkController.isOffline(wallet) } returns true
        val useCase = createUseCase()

        useCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        advanceUntilIdle()

        coVerify(exactly = 1) { networkController.resume(wallet) }
    }

    /** A member that cannot be resumed keeps its row: "online" over a paused network is worse. */
    @Test
    fun resetIfBlockchainRemoved_resumeFails_keepsRow() = runTest(dispatcher) {
        coEvery { walletManager.getWallets(account) } returns emptyList()
        every { networkController.isOffline(wallet) } returns true
        coEvery { networkController.resume(wallet) } throws IOException("resume failed")
        val useCase = createUseCase()

        useCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineModeManager.resetChain(any(), any()) }
    }

    @Test
    fun withTemporaryOnline_nonActiveAccountNoLiveMembers_entersAndExitsWithMatchingToken() = runTest(dispatcher) {
        val useCase = createUseCase(members = emptyList()) // non-active account: no live kit/adapter
        val enterToken = slot<Long>()
        val exitToken = slot<Long>()

        val result = useCase.withTemporaryOnline(account, BlockchainType.Zcash) { "sent" }

        assertEquals("sent", result)
        coVerify { offlineModeManager.enterTemporaryOnline(key, capture(enterToken)) }
        coVerify { offlineModeManager.exitTemporaryOnline(key, capture(exitToken)) }
        assertEquals(enterToken.captured, exitToken.captured)
        coVerify(exactly = 0) { networkController.pause(any()) }
        coVerify(exactly = 0) { networkController.resume(any()) }
    }

    @Test
    fun forgetAccounts_racesWithSetChainOffline_processedStrictlySequentially() = runTest(dispatcher) {
        every { networkController.isOffline(wallet) } returns false
        val insideCriticalSection = AtomicBoolean(false)
        val sawOverlap = AtomicBoolean(false)
        coEvery { networkController.pause(wallet) } coAnswers {
            if (!insideCriticalSection.compareAndSet(false, true)) sawOverlap.set(true)
            delay(10)
            insideCriticalSection.set(false)
        }
        coEvery { offlineModeManager.forgetAccounts(listOf(ACCOUNT_ID)) } coAnswers {
            if (!insideCriticalSection.compareAndSet(false, true)) sawOverlap.set(true)
            delay(10)
            insideCriticalSection.set(false)
        }
        val useCase = createUseCase()

        val toggle = launch { useCase.setChainOffline(account, BlockchainType.Zcash, true) }
        val forget = launch { useCase.forgetAccounts(listOf(ACCOUNT_ID)) }
        toggle.join()
        forget.join()

        assertFalse(sawOverlap.get())
    }

    /** Outcomes are logged verbatim, and an account type can hold key material. */
    @Test
    fun memberOutcomeToString_omitsWallet() {
        val text = MemberOutcome(wallet, target = true, LifecycleOutcome.Applied).toString()

        assertFalse(text.contains(wallet.account.toString()))
        assertTrue(text.contains(wallet.tokenQueryId))
    }

    private companion object {
        const val ACCOUNT_ID = "offline-usecase-account"
        const val LIFECYCLE_TIMEOUT_MS = 15_000L
    }
}

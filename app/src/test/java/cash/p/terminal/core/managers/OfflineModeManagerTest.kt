package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.OfflineModeStorage
import cash.p.terminal.entities.OfflineBlockchain
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.zcashTransparentWallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val storage = mockk<OfflineModeStorage>(relaxed = true)

    private val wallet = zcashTransparentWallet(zcashMnemonicAccount(ACCOUNT_ID))
    private val key = OfflineKey(ACCOUNT_ID, BlockchainType.Zcash)
    private val adapter = mockk<IBalanceAdapter>(relaxed = true)

    private fun createManager(stored: List<OfflineBlockchain> = emptyList()): OfflineModeManager {
        every { storage.getAll() } returns stored
        return OfflineModeManager(storage, TestDispatcherProvider(dispatcher, TestScope(dispatcher)))
    }

    private fun offlineRow(offline: Boolean, lastSyncedAt: Long? = null) = OfflineBlockchain(
        accountId = ACCOUNT_ID,
        blockchainType = BlockchainType.Zcash,
        offline = offline,
        enabledAt = if (offline) ENABLED_AT else null,
        lastSyncedAt = lastSyncedAt,
    )

    @Test
    fun isNetworkPaused_storedOfflineRow_readAtConstruction() {
        val manager = createManager(listOf(offlineRow(offline = true)))

        assertTrue(manager.isNetworkPaused(key))
    }

    @Test
    fun isNetworkPaused_storedOnlineRow_returnsFalse() {
        val manager = createManager(listOf(offlineRow(offline = false)))

        assertFalse(manager.isNetworkPaused(key))
    }

    @Test
    fun isNetworkPaused_temporaryOnline_outranksStoredOffline() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))

        manager.enterTemporaryOnline(key, TOKEN)
        assertFalse(manager.isNetworkPaused(key))

        manager.exitTemporaryOnline(key, TOKEN)
        assertTrue(manager.isNetworkPaused(key))
    }

    @Test
    fun isNetworkPaused_transitionInFlight_pausesWithoutStoredRow() = runTest(dispatcher) {
        val manager = createManager()

        manager.beginTransition(key, TOKEN)
        assertTrue(manager.isNetworkPaused(key))

        manager.endTransition(key, TOKEN)
        assertFalse(manager.isNetworkPaused(key))
    }

    @Test
    fun endTransition_unknownToken_keepsOwnerTokenActive() = runTest(dispatcher) {
        val manager = createManager()

        manager.beginTransition(key, TOKEN)
        manager.endTransition(key, OTHER_TOKEN)

        assertTrue(manager.isNetworkPaused(key))
    }

    @Test
    fun seedLastSynced_noStoredDate_seedsFromBlockTimestampInMillis() = runTest(dispatcher) {
        val manager = createManager()

        manager.seedLastSynced(wallet, BLOCK_TIME_SEC)

        coVerify { storage.setLastSynced(ACCOUNT_ID, BlockchainType.Zcash, BLOCK_TIME_SEC * 1000) }
        assertEquals(BLOCK_TIME_SEC * 1000, manager.lastSyncedAt(key))
    }

    @Test
    fun seedLastSynced_storedDatePresent_doesNotOverwrite() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = false, lastSyncedAt = STORED_DATE)))

        manager.seedLastSynced(wallet, BLOCK_TIME_SEC)

        coVerify(exactly = 0) { storage.setLastSynced(any(), any(), any()) }
        assertEquals(STORED_DATE, manager.lastSyncedAt(key))
    }

    @Test
    fun onBalanceState_notSyncedToSynced_stampsDate() = runTest(dispatcher) {
        val manager = createManager()
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)

        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 1) { storage.setLastSynced(ACCOUNT_ID, BlockchainType.Zcash, any()) }
        assertNotNull(manager.lastSyncedAt(key))
    }

    @Test
    fun onBalanceState_alreadySynced_doesNotStampAgain() = runTest(dispatcher) {
        val manager = createManager()
        manager.onSubscribed(wallet, adapter, AdapterState.Synced)

        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 0) { storage.setLastSynced(any(), any(), any()) }
    }

    @Test
    fun onBalanceState_networkPaused_doesNotStamp() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)

        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 0) { storage.setLastSynced(any(), any(), any()) }
    }

    @Test
    fun onBalanceState_afterAdapterGone_ignoresLateEmission() = runTest(dispatcher) {
        val manager = createManager()
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)

        manager.onAdapterGone(wallet)
        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 0) { storage.setLastSynced(any(), any(), any()) }
        assertNull(manager.lastSyncedAt(key))
    }

    @Test
    fun onBalanceState_storageFails_doesNotPublishDate() = runTest(dispatcher) {
        val manager = createManager()
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)
        coEvery { storage.setLastSynced(any(), any(), any()) } throws IOException("disk full")

        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        assertNull(manager.lastSyncedAt(key))
    }

    @Test
    fun persistAndPublish_disablingOffline_keepsEnabledAt() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))

        manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = false)

        coVerify { storage.setMode(ACCOUNT_ID, BlockchainType.Zcash, false, ENABLED_AT) }
        assertFalse(manager.isNetworkPaused(key))
        assertEquals(ENABLED_AT, manager.stateFlow.value[key]?.enabledAt)
    }

    @Test
    fun persistAndPublish_forgottenAccount_writesNothing() = runTest(dispatcher) {
        val manager = createManager()
        manager.forgetAccounts(listOf(ACCOUNT_ID))

        manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = true)

        coVerify(exactly = 0) { storage.setMode(any(), any(), any(), any()) }
        assertFalse(manager.isNetworkPaused(key))
    }

    @Test
    fun resetChain_storedRow_dropsModeAndSyncDate() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true, lastSyncedAt = STORED_DATE)))

        manager.resetChain(ACCOUNT_ID, BlockchainType.Zcash)

        coVerify(exactly = 1) { storage.deleteChain(ACCOUNT_ID, BlockchainType.Zcash) }
        assertFalse(manager.isNetworkPaused(key))
        assertNull(manager.lastSyncedAt(key))
    }

    @Test
    fun resetChain_noStoredRow_writesNothing() = runTest(dispatcher) {
        val manager = createManager()

        manager.resetChain(ACCOUNT_ID, BlockchainType.Zcash)

        coVerify(exactly = 0) { storage.deleteChain(any(), any()) }
    }

    @Test
    fun forgetAccounts_dropsStoredRowsAndPendingTokens() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))
        manager.beginTransition(key, TOKEN)

        manager.forgetAccounts(listOf(ACCOUNT_ID))

        coVerify { storage.deleteByAccount(ACCOUNT_ID) }
        assertFalse(manager.isNetworkPaused(key))
        assertTrue(manager.stateFlow.value.isEmpty())
    }

    @Test
    fun onBalanceState_replacedAdapter_ignoresPreviousAdapterEmission() = runTest(dispatcher) {
        val manager = createManager()
        val replacement = mockk<IBalanceAdapter>(relaxed = true)
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)

        manager.onAdapterGone(wallet)
        manager.onSubscribed(wallet, replacement, AdapterState.Connecting)
        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 0) { storage.setLastSynced(any(), any(), any()) }
        assertNull(manager.lastSyncedAt(key))
    }

    @Test
    fun onBalanceState_syncedTransition_keepsOfflineFlagAndEnabledAt() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))
        manager.onSubscribed(wallet, adapter, AdapterState.Connecting)
        manager.enterTemporaryOnline(key, TOKEN)

        manager.onBalanceState(wallet, adapter, AdapterState.Synced)

        coVerify(exactly = 0) { storage.setMode(any(), any(), any(), any()) }
        assertNotNull(manager.lastSyncedAt(key))
        assertTrue(manager.stateFlow.value.getValue(key).offline)
        assertEquals(ENABLED_AT, manager.stateFlow.value.getValue(key).enabledAt)
    }

    @Test
    fun persistAndPublish_enablingOffline_keepsLastSyncedAt() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = false, lastSyncedAt = STORED_DATE)))

        manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = true)

        assertEquals(STORED_DATE, manager.lastSyncedAt(key))
    }

    @Test
    fun persistAndPublish_storageFails_doesNotPublish() = runTest(dispatcher) {
        val manager = createManager()
        coEvery { storage.setMode(any(), any(), any(), any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = true)
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertFalse(manager.isNetworkPaused(key))
    }

    @Test
    fun persistAndPublish_otherAccount_leavesFirstAccountUntouched() = runTest(dispatcher) {
        val manager = createManager(listOf(offlineRow(offline = true)))
        val otherKey = OfflineKey(OTHER_ACCOUNT_ID, BlockchainType.Zcash)

        manager.persistAndPublish(OTHER_ACCOUNT_ID, BlockchainType.Zcash, offline = false)
        manager.forgetAccounts(listOf(OTHER_ACCOUNT_ID))

        coVerify(exactly = 0) { storage.deleteByAccount(ACCOUNT_ID) }
        assertFalse(manager.isNetworkPaused(otherKey))
        assertTrue(manager.isNetworkPaused(key))
    }

    /** The row write and the deletion share one critical section, so a toggle cannot resurrect it. */
    @Test
    fun persistAndPublish_accountForgottenMidWrite_leavesNoRow() = runTest(dispatcher) {
        val manager = createManager()
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        coEvery { storage.setMode(any(), any(), any(), any()) } coAnswers {
            writeStarted.complete(Unit)
            releaseWrite.await()
        }

        val toggle = launch { manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = true) }
        writeStarted.await()
        val forget = launch { manager.forgetAccounts(listOf(ACCOUNT_ID)) }
        releaseWrite.complete(Unit)
        toggle.join()
        forget.join()

        coVerifyOrder {
            storage.setMode(ACCOUNT_ID, BlockchainType.Zcash, true, any())
            storage.deleteByAccount(ACCOUNT_ID)
        }
        assertFalse(manager.isNetworkPaused(key))
        assertTrue(manager.stateFlow.value.isEmpty())
    }

    @Test
    fun exitTemporaryOnline_overlappingSession_keepsOtherPermission() = runTest(dispatcher) {
        val manager = createManager()
        manager.persistAndPublish(ACCOUNT_ID, BlockchainType.Zcash, offline = true)
        manager.enterTemporaryOnline(key, TOKEN)
        manager.enterTemporaryOnline(key, OTHER_TOKEN)

        manager.exitTemporaryOnline(key, TOKEN)

        assertFalse(manager.isNetworkPaused(key))
    }

    private companion object {
        const val ACCOUNT_ID = "zcash-account"
        const val OTHER_ACCOUNT_ID = "other-zcash-account"
        const val ENABLED_AT = 1_700_000_000_000L
        const val STORED_DATE = 1_700_000_500_000L
        const val BLOCK_TIME_SEC = 1_700_000_900L
        const val TOKEN = 1L
        const val OTHER_TOKEN = 2L
    }
}

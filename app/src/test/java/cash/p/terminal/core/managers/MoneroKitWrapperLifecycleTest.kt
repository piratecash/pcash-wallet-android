package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MoneroKitWrapperLifecycleTest : MoneroKitWrapperTestFixture() {
    @Test
    fun startAccountService_migrationReplayFailure_remainsPendingAndRetries() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroTrezorOperationGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        val events = mutableListOf<String>()
        var refreshAttempts = 0
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.MigrationReplayRequired
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } returns status
        every { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) } answers {
            events += "refresh"
            if (refreshAttempts++ == 0) error("refresh failed")
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway"
            secondArg<(String) -> Any?>().invoke("wallet-key")
        }
        every {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.MigrationReplayPending,
            )
        } answers { events += "pending" }

        assertTrue(
            runCatching {
                wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
            }.isFailure,
        )
        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(
            listOf("pending", "gateway", "refresh", "pending", "gateway", "refresh"),
            events,
        )
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    @Test
    fun normalizedLiveRefreshState_durableRecoveryObligationsAreNeverDowngraded() {
        assertSame(
            MoneroSpentReconciliationState.MigrationReplayPending,
            MoneroSpentReconciliationState.MigrationReplayPending.normalizedLiveRefreshState(),
        )
        assertSame(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending.normalizedLiveRefreshState(),
        )
    }

    @Test
    fun explicitColdRecovery_doesNotRequireControlledLiveRefreshFinalization() {
        assertFalse(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.LiveRefreshPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.MigrationReplayPending
                .requiresControlledRefreshFinalization(),
        )
    }

    @Test
    fun reconciliationFailure_remainsActionableForTrezorPreparation() {
        assertTrue(MoneroSpendReadiness.ReconciliationFailed.requiresTrezorPreparation())
        assertTrue(MoneroSpendReadiness.NeedsKeyImageSync.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Syncing.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.CheckingKeyImages.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.ReconcilingSpentStatus.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Ready.requiresTrezorPreparation())
    }

    @Test
    fun handleStartFailure_explicitColdRecovery_clearsSessionMarkerBeforeRetry() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        setExplicitColdRecoveryPending(wrapper, true)

        runCatching {
            invokeHandleStartFailure(wrapper, IllegalStateException("cold recovery failed"))
        }

        assertFalse(explicitColdRecoveryPending(wrapper))
    }

    @Test
    fun saveSynced_syncingAfterQueuedSyncedEvent_doesNotStoreOrClearRescan() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Syncing())

        val stored = wrapper.saveSynced()

        assertFalse(stored)
        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
        }
    }

    @Test
    fun abandonFaultedWallet_readyHardwareWallet_invalidatesStateAndOwnership() {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.StoreFailed,
            "store failed",
        )
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbandonFaultedWallet(wrapper, failure)

        verify(exactly = 1) { service.abandonFaultedWallet() }
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun startFailure_hardwareCleanupFailureRetainsNativeOwnershipButFailsClosed() {
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        val failure = IllegalStateException("start failed")
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns false
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching { invokeHandleStartFailure(wrapper, failure) }.exceptionOrNull()

        assertSame(failure, (thrown as InvocationTargetException).targetException)
        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
    }

    @Test
    fun startFailure_hardwareGatewayPreflightFailure_exposesRetryableTerminalReadiness() {
        val failure = IllegalStateException("device unavailable")
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns true

        runCatching { invokeHandleStartFailure(wrapper, failure) }

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_readyHardwareWallet_invalidatesReadinessBeforeItCanReopen() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every { service.stop(true) } returns true
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        wrapper.stop()

        verify(exactly = 1) { service.stop(true) }
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun send_pendingHardwareReconciliation_failsBeforePauseGatewayOrPreparation() = runTest {
        val service = mockService()
        val gateway = mockk<MoneroTrezorOperationGateway>(relaxed = true)
        val wrapper = createWrapper(service, trezorAccount, gateway = gateway)
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertTrue(runCatching { wrapper.send(BigDecimal.ONE, "address", null) }.isFailure)

        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) { service.prepareTransaction(any()) }
        coVerify(exactly = 0) { gateway.execute<Any?>(trezorAccount, any()) }
    }

    @Test
    fun submitSignedRawTransaction_pendingHardwareReconciliation_neverSubmitsOrLoadsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every {
            restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
        } returns MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.ReconcilingSpentStatus)

        val failure = try {
            wrapper.submitSignedRawTransaction(byteArrayOf(1, 2, 3))
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertTrue(failure != null)
        coVerify(exactly = 0) { service.submitSignedRawTransaction(any()) }
    }

    @Test
    fun completedSubmittedRawTransaction_resumeFailureFailsClosedButPreservesResult() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        val result = RawMoneroBroadcastResult.Submitted("tx-id")
        val failure = IllegalStateException("resume failed")
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        val actual = wrapper.completeHardwareOperation(result, failure, true)
        assertSame(result, actual)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_callbackDuringNativeClose_cannotRecreateReconciliationWork() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        var callbackAccepted: Boolean? = null
        every { service.stop(true) } answers {
            callbackAccepted = wrapper.onRefreshed(wallet = null, full = true)
            true
        }
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        invokeActivateReconciliationSession(wrapper)

        wrapper.stop()

        assertFalse(callbackAccepted ?: true)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun reconciliationReadiness_legacyAndPendingNeverExposeReady() {
        assertFalse(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.LiveRefreshPending,
            ),
        )
        assertTrue(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.Ready,
            ),
        )
    }

    @Test
    fun reconciliationCallback_matchingIsGenerationBased() {
        assertFalse(isMatchingReconciliationCallback(4, 3))
        assertTrue(isMatchingReconciliationCallback(4, 4))
    }

    @Test
    fun walletStartedCallback_activeSessionIsAcceptedDuringItsStartOrAfterward() {
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = true,
            ),
        )
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = true,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = false,
                isStarted = false,
                startInProgress = true,
            ),
        )
    }

    @Test
    fun walletStartedSyncState_nativeConnectedBeforeServiceStatePublished_staysSyncingUntilHealthy() {
        assertTrue(
            walletStartedSyncState(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ) is AdapterState.Syncing,
        )
        assertSame(
            AdapterState.Synced,
            walletStartedSyncState(healthyWalletHealth()),
        )
    }

    @Test
    fun nativeHealthFailure_serviceStateLagIsTransientButNativeFailuresRemainErrors() {
        assertFalse(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(callbackWalletIsCurrent = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeConnectionIsConnected = false),
            ),
        )
    }

    @Test
    fun reconciliationCallback_failedNativeStatusIsConsumedFailClosed() {
        val callbackIsSuccessful = canFinalizeSpentReconciliation(
            healthyWalletHealth(nativeStatusIsOk = false),
        )

        assertEquals(
            ReconciliationCallbackDisposition.FailClosed,
            reconciliationCallbackDisposition(
                awaitingGeneration = 4,
                callbackGeneration = 4,
                callbackIsSuccessful = callbackIsSuccessful,
            ),
        )
    }

    @Test
    fun reconciliationCallback_requiresSuccessfulNativeStatusConnectionAndSynchronization() {
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(serviceConnectionIsConnected = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(walletIsSynchronized = false),
            ),
        )
        assertTrue(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(),
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedAndUntrustedResultsTakeDifferentSafePaths() {
        assertEquals(
            ColdKeyImageSyncNextStep.TrustedReady,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = false,
            ),
        )
        assertEquals(
            ColdKeyImageSyncNextStep.PreserveKeyImagesRescan,
            coldKeyImageSyncNextStep(
                spentStatusVerified = false,
                hasUnknownKeyImages = false,
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedResultWithUnknownKeyImagesRemainsFailClosed() {
        assertEquals(
            ColdKeyImageSyncNextStep.NeedsKeyImageSync,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = true,
            ),
        )
    }

    @Test
    fun trustedKeyImageSyncFinalization_onlyReadyCanAuthorizeSpending() {
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.Ready,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Syncing(),
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = true,
                syncState = AdapterState.Syncing(),
            ),
        )
    }

    @Test
    fun spentStatusRequestRetry_isWiredToTerminalFailure() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.Retry)

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun spentStatusRequestNeedsKeyImageSync_isTerminalAndClearsOperation() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        assertTrue(reconciler.beginRecovery(session) != null)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.NeedsKeyImageSync)

        assertSame(MoneroSpendReadiness.NeedsKeyImageSync, wrapper.spendReadiness.value)
        assertFalse(reconciler.hasActiveOperation(session))
    }

    @Test
    fun terminalReadiness_clearsOnlyItsMatchingKeyImageSyncMarker() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        listOf(
            MoneroSpendReadiness.Ready,
            MoneroSpendReadiness.NeedsKeyImageSync,
            MoneroSpendReadiness.ReconciliationFailed,
        ).forEach { terminalReadiness ->
                setKeyImageSyncSession(wrapper, session)
                invokeSetSpendReadinessForSession(wrapper, session, terminalReadiness)

                assertSame(terminalReadiness, wrapper.spendReadiness.value)
                assertEquals(null, keyImageSyncSession(wrapper))
            }

        invokeActivateReconciliationSession(wrapper)
        val newerSession = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, newerSession)

        invokeSetSpendReadinessForSession(wrapper, session, MoneroSpendReadiness.Ready)

        assertEquals(newerSession, keyImageSyncSession(wrapper))
    }


}

package cash.p.terminal.core.managers

internal interface MoneroSpentStatusRescanRequest {
    fun armAndRequest()
}

internal interface MoneroSpentStatusRecoveryOperations<Wallet : Any> {
    fun isFullyHealthy(wallet: Wallet): Boolean
    fun hasUnknownKeyImages(wallet: Wallet): Boolean
    suspend fun performPreservingRescan(
        wallet: Wallet,
        request: MoneroSpentStatusRescanRequest,
    )
    fun requestPreservingRescan(wallet: Wallet)
    suspend fun storeReconciledWallet(wallet: Wallet)
    fun persistReady()
}

internal enum class MoneroSpentStatusRequestResult {
    Retry,
    NeedsKeyImageSync,
    AwaitingCallback,
}

internal class MoneroSpentStatusRecovery<Wallet : Any>(
    private val reconciler: MoneroSpentStatusReconciler,
    private val operations: MoneroSpentStatusRecoveryOperations<Wallet>,
) {
    suspend fun request(
        session: Long,
        wallet: Wallet,
    ): MoneroSpentStatusRequestResult {
        preflightResult(session, wallet)?.let { return it }
        val request = PreservingRescanRequest(session, wallet)
        try {
            operations.performPreservingRescan(wallet, request)
            check(request.wasRequested) {
                "Monero spent-status reconciliation rescan was not requested"
            }
        } catch (error: Throwable) {
            reconciler.clearOperation(session)
            throw error
        }

        return MoneroSpentStatusRequestResult.AwaitingCallback
    }

    private fun preflightResult(
        session: Long,
        wallet: Wallet,
    ): MoneroSpentStatusRequestResult? {
        val result = when {
            !operations.isFullyHealthy(wallet) -> MoneroSpentStatusRequestResult.Retry
            operations.hasUnknownKeyImages(wallet) ->
                MoneroSpentStatusRequestResult.NeedsKeyImageSync
            else -> null
        }
        if (result != null) reconciler.clearOperation(session)
        return result
    }

    fun acceptCallback(
        session: Long,
        generation: Long,
        callbackIsSuccessful: Boolean,
    ): ReconciliationCallbackDisposition =
        reconciler.callbackDisposition(session, generation, callbackIsSuccessful)

    suspend fun finalizeAcceptedCallback(
        session: Long,
        wallet: Wallet,
    ) {
        try {
            requireHealthyKnownKeyImages(wallet)
            operations.storeReconciledWallet(wallet)
            requireHealthyKnownKeyImages(wallet)
            operations.persistReady()
            reconciler.clearOperation(session)
        } catch (error: Throwable) {
            reconciler.clearOperation(session)
            throw error
        }
    }

    private fun requireHealthyKnownKeyImages(wallet: Wallet) {
        check(operations.isFullyHealthy(wallet)) {
            "Monero reconciliation callback is no longer successful"
        }
        check(!operations.hasUnknownKeyImages(wallet)) {
            "Monero reconciliation completed with unknown key images"
        }
    }

    private inner class PreservingRescanRequest(
        private val session: Long,
        private val wallet: Wallet,
    ) : MoneroSpentStatusRescanRequest {
        var wasRequested = false
            private set

        override fun armAndRequest() {
            check(operations.isFullyHealthy(wallet)) {
                "Monero wallet is not ready for spent-status reconciliation"
            }
            check(!operations.hasUnknownKeyImages(wallet)) {
                "Monero wallet has unknown key images"
            }
            val generation = checkNotNull(reconciler.beginRequest(session)) {
                "Monero spent-status reconciliation request is not allowed"
            }
            check(reconciler.markAwaitingCallback(session, generation)) {
                "Failed to arm Monero spent-status reconciliation callback"
            }
            operations.requestPreservingRescan(wallet)
            wasRequested = true
        }
    }
}

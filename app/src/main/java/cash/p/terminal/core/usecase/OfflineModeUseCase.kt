package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.OfflineNetworkController
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class LifecycleOutcome { Applied, NotApplied, Unknown }

data class MemberOutcome(val wallet: Wallet, val target: Boolean, val outcome: LifecycleOutcome) {
    /** Never print the wallet: it carries the account, whose type can hold key material. */
    override fun toString() = "MemberOutcome(${wallet.tokenQueryId}, target=$target, $outcome)"
}

sealed interface TransitionResult {
    data object Success : TransitionResult
    data class Failed(val cause: Throwable) : TransitionResult
    data class Degraded(val members: List<MemberOutcome>) : TransitionResult
}

private const val LIFECYCLE_TIMEOUT_MS = 15_000L

/**
 * Sole owner of offline-mode transitions for (account, blockchainType) pairs. Mode toggles, account
 * deletion, and temporary-online overrides all go through the same single-worker FIFO queue, so they
 * never race each other or duplicate a kit-level operation on the same wallet.
 */
class OfflineModeUseCase(
    private val offlineModeManager: OfflineModeManager,
    private val networkController: OfflineNetworkController,
    private val walletManager: IWalletManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val tokenCounter = AtomicLong(0)
    private val pending = ConcurrentHashMap<Wallet, Deferred<Unit>>()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val workerScope = dispatcherProvider.applicationScope

    init {
        workerScope.launch { runWorker() }
    }

    suspend fun setChainOffline(account: Account, blockchainType: BlockchainType, offline: Boolean): TransitionResult =
        enqueueAndAwait { deferred -> Command.SetOffline(account, blockchainType, offline, deferred) }

    suspend fun forgetAccounts(accountIds: List<String>) {
        enqueueAndAwait { deferred -> Command.Forget(accountIds, deferred) }
    }

    /**
     * Call after removing wallets: a chain the account no longer holds must not stay paused. Decided
     * here, not in the worker — a re-add before the queued command runs must not preserve the row.
     * Runs on the application scope so closing the screen right after the deletion cannot drop it.
     */
    fun resetIfBlockchainRemoved(account: Account, blockchainType: BlockchainType) {
        workerScope.launch {
            try {
                // Stored wallets, not the active ones: this account may not be the active one.
                val stillHeld = walletManager.getWallets(account)
                    .any { it.token.blockchainType == blockchainType }
                if (stillHeld) return@launch

                enqueueAndAwait<Unit> { deferred -> Command.ResetChain(account.id, blockchainType, deferred) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Failed to reset offline mode for (${account.id}, $blockchainType)")
            }
        }
    }

    suspend fun <T> withTemporaryOnline(account: Account, blockchainType: BlockchainType, block: suspend () -> T): T {
        val token = tokenCounter.incrementAndGet()
        enqueueAndAwait { deferred -> Command.EnterTemporaryOnline(account, blockchainType, token, deferred) }
        try {
            return block()
        } finally {
            restoreAfterTemporaryOnline(account, blockchainType, token)
        }
    }

    private suspend fun restoreAfterTemporaryOnline(account: Account, blockchainType: BlockchainType, token: Long) {
        try {
            enqueueAndAwait<Unit> { deferred ->
                Command.ExitTemporaryOnline(account, blockchainType, token, deferred)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Failed to restore offline mode for (${account.id}, $blockchainType) after temporary online")
        }
    }

    private suspend fun <T> enqueueAndAwait(build: (CompletableDeferred<T>) -> Command): T {
        val deferred = CompletableDeferred<T>()
        val result = commands.trySend(build(deferred))
        if (result.isFailure) {
            deferred.completeExceptionally(
                result.exceptionOrNull() ?: CancellationException("OfflineModeUseCase queue is closed")
            )
        }
        return deferred.await()
    }

    private suspend fun runWorker() {
        try {
            for (command in commands) {
                process(command)
            }
        } finally {
            drainOnCancellation()
        }
    }

    private fun drainOnCancellation() {
        commands.close()
        val cause = CancellationException("OfflineModeUseCase worker cancelled")
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            when (command) {
                is Command.SetOffline -> command.deferred.completeExceptionally(cause)
                is Command.Forget -> command.deferred.completeExceptionally(cause)
                is Command.EnterTemporaryOnline -> command.deferred.completeExceptionally(cause)
                is Command.ExitTemporaryOnline -> command.deferred.completeExceptionally(cause)
                is Command.ResetChain -> command.deferred.completeExceptionally(cause)
            }
        }
    }

    private suspend fun process(command: Command) {
        when (command) {
            is Command.SetOffline -> process(command)

            is Command.Forget -> complete(command.deferred) {
                offlineModeManager.forgetAccounts(command.accountIds)
            }

            is Command.EnterTemporaryOnline -> complete(command.deferred) {
                doEnterTemporaryOnline(command.account, command.blockchainType, command.token)
            }

            is Command.ExitTemporaryOnline -> complete(command.deferred) {
                doExitTemporaryOnline(command.account, command.blockchainType, command.token)
            }

            is Command.ResetChain -> complete(command.deferred) {
                doResetChain(command.accountId, command.blockchainType)
            }
        }
    }

    private suspend fun complete(deferred: CompletableDeferred<Unit>, block: suspend () -> Unit) {
        try {
            block()
            deferred.complete(Unit)
        } catch (e: CancellationException) {
            deferred.completeExceptionally(e)
            throw e
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
        }
    }

    private suspend fun process(command: Command.SetOffline) {
        var result: TransitionResult = TransitionResult.Failed(CancellationException("interrupted"))
        try {
            result = doSetOffline(command.account, command.blockchainType, command.offline)
        } catch (e: CancellationException) {
            result = TransitionResult.Failed(e)
            throw e
        } catch (e: Throwable) {
            result = TransitionResult.Failed(e)
        } finally {
            command.deferred.complete(result)
        }
    }

    private suspend fun doSetOffline(
        account: Account,
        blockchainType: BlockchainType,
        offline: Boolean,
    ): TransitionResult {
        val key = OfflineKey(account.id, blockchainType)
        val token = tokenCounter.incrementAndGet()
        offlineModeManager.beginTransition(key, token)
        try {
            val outcomes = applyBatch(account.id, blockchainType, offline)
                ?: return TransitionResult.Degraded(unknown(account.id, blockchainType, offline))

            if (outcomes.any { it.outcome == LifecycleOutcome.Unknown }) {
                return TransitionResult.Degraded(outcomes)
            }

            if (outcomes.any { it.outcome == LifecycleOutcome.NotApplied }) {
                return compensate(outcomes, offline)
            }

            return try {
                offlineModeManager.persistAndPublish(account.id, blockchainType, offline)
                TransitionResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                compensate(outcomes, offline, writeFailure = e)
            }
        } finally {
            offlineModeManager.endTransition(key, token)
        }
    }

    private suspend fun compensate(
        outcomes: List<MemberOutcome>,
        offline: Boolean,
        writeFailure: Throwable? = null,
    ): TransitionResult {
        val appliedMembers = outcomes.filter { it.outcome == LifecycleOutcome.Applied }.map { it.wallet }
        val compensationOutcomes = applyBatch(appliedMembers, !offline)
            ?: unknown(appliedMembers, !offline)

        return if (compensationOutcomes.all { it.outcome == LifecycleOutcome.Applied }) {
            val default = IllegalStateException("Failed to apply offline=$offline to one or more members")
            writeFailure?.let { TransitionResult.Failed(it) } ?: TransitionResult.Failed(default)
        } else {
            TransitionResult.Degraded(outcomes + compensationOutcomes)
        }
    }

    /**
     * Resuming comes first: dropping the row while a member is still paused would publish "online"
     * over a paused network. If a member cannot be resumed, the row stays — paused and saying so.
     */
    private suspend fun doResetChain(accountId: String, blockchainType: BlockchainType) {
        val outcomes = applyBatch(accountId, blockchainType, target = false)
            ?: unknown(accountId, blockchainType, target = false)
        if (outcomes.any { it.outcome != LifecycleOutcome.Applied }) {
            Timber.w("Kept offline row for ($accountId, $blockchainType), resume failed: $outcomes")
            return
        }
        offlineModeManager.resetChain(accountId, blockchainType)
    }

    private suspend fun doEnterTemporaryOnline(account: Account, blockchainType: BlockchainType, token: Long) {
        val key = OfflineKey(account.id, blockchainType)
        var installed = false
        val outcomes = try {
            applyBatch(
                members = liveMembers(account.id, blockchainType),
                target = false,
                before = {
                    offlineModeManager.enterTemporaryOnline(key, token)
                    installed = true
                },
                include = { it },
            ) ?: error("Pending offline lifecycle operation for $key")
        } catch (e: Throwable) {
            if (installed) offlineModeManager.exitTemporaryOnline(key, token)
            throw e
        }
        if (outcomes.all { it.outcome == LifecycleOutcome.Applied }) return
        offlineModeManager.exitTemporaryOnline(key, token)
        val appliedMembers = outcomes.filter { it.outcome == LifecycleOutcome.Applied }.map { it.wallet }
        val compensationOutcomes = applyBatch(appliedMembers, target = true) ?: unknown(appliedMembers, target = true)
        val pendingOperations = (outcomes + compensationOutcomes)
            .filter { it.outcome == LifecycleOutcome.Unknown }
            .mapNotNull { pending[it.wallet] }
        if (pendingOperations.isNotEmpty() || compensationOutcomes.any { it.outcome != LifecycleOutcome.Applied }) {
            workerScope.launch {
                pendingOperations.forEach { it.join() }
                restoreAfterTemporaryOnline(account, blockchainType, token)
            }
        }
        error("Failed to enter temporary online mode for $key: $outcomes")
    }

    private suspend fun doExitTemporaryOnline(account: Account, blockchainType: BlockchainType, token: Long) {
        val key = OfflineKey(account.id, blockchainType)
        offlineModeManager.exitTemporaryOnline(key, token)
        // Re-read the current desired state rather than trusting a snapshot taken before the block ran:
        // another command may have changed it while temporary-online was active.
        val target = offlineModeManager.isNetworkPaused(key)
        val outcomes = applyBatch(account.id, blockchainType, target)
            ?: unknown(account.id, blockchainType, target)
        if (outcomes.any { it.outcome != LifecycleOutcome.Applied }) {
            Timber.w("Failed to fully restore offline state for $key after temporary online: $outcomes")
        }
    }

    private fun liveMembers(accountId: String, blockchainType: BlockchainType): List<Wallet> =
        walletManager.activeWallets.filter {
            it.account.id == accountId && it.token.blockchainType == blockchainType
        }

    private suspend fun applyBatch(
        accountId: String, blockchainType: BlockchainType, target: Boolean, before: suspend () -> Unit = {},
    ) =
        applyBatch(liveMembers(accountId, blockchainType), target, before)

    private suspend fun applyBatch(
        members: List<Wallet>,
        target: Boolean,
        before: suspend () -> Unit = {},
        include: (Boolean) -> Boolean = { true },
    ): List<MemberOutcome>? {
        if (!drainPending(members)) return null
        val states = members.associateWith(networkController::isOffline)
        before()
        return members.filter { include(states.getValue(it)) }
            .map { member -> applyToMember(member, target, states.getValue(member)) }
    }

    private fun unknown(id: String, chain: BlockchainType, target: Boolean) = unknown(liveMembers(id, chain), target)

    private fun unknown(members: List<Wallet>, target: Boolean) =
        members.map { MemberOutcome(it, target, LifecycleOutcome.Unknown) }

    private suspend fun applyToMember(member: Wallet, target: Boolean, isOffline: Boolean): MemberOutcome {
        if (isOffline == target) {
            return MemberOutcome(member, target, LifecycleOutcome.Applied)
        }
        val op = workerScope.async(dispatcherProvider.io) {
            if (target) networkController.pause(member) else networkController.resume(member)
        }
        return try {
            when (withTimeoutOrNull(LIFECYCLE_TIMEOUT_MS) { op.await() }) {
                null -> MemberOutcome(member, target, LifecycleOutcome.Unknown).also { pending[member] = op }
                else -> MemberOutcome(member, target, LifecycleOutcome.Applied)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            MemberOutcome(member, target, LifecycleOutcome.NotApplied)
        }
    }

    private suspend fun drainPending(members: List<Wallet>) = members.map { drainPending(it) }.all { it }

    private suspend fun drainPending(member: Wallet): Boolean {
        val stale = pending.remove(member) ?: return true
        val stillPending = try {
            withTimeoutOrNull(LIFECYCLE_TIMEOUT_MS) { stale.await(); false } ?: true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (stillPending) pending[member] = stale
        return !stillPending
    }

    private sealed interface Command {
        data class SetOffline(
            val account: Account,
            val blockchainType: BlockchainType,
            val offline: Boolean,
            val deferred: CompletableDeferred<TransitionResult>,
        ) : Command

        data class Forget(
            val accountIds: List<String>,
            val deferred: CompletableDeferred<Unit>,
        ) : Command

        data class EnterTemporaryOnline(
            val account: Account,
            val blockchainType: BlockchainType,
            val token: Long,
            val deferred: CompletableDeferred<Unit>,
        ) : Command

        data class ExitTemporaryOnline(
            val account: Account,
            val blockchainType: BlockchainType,
            val token: Long,
            val deferred: CompletableDeferred<Unit>,
        ) : Command

        data class ResetChain(
            val accountId: String,
            val blockchainType: BlockchainType,
            val deferred: CompletableDeferred<Unit>,
        ) : Command
    }
}

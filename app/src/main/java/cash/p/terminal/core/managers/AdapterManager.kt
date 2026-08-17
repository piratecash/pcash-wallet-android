package cash.p.terminal.core.managers

import android.os.HandlerThread
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.ZcashRescanException
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.litecoinMwebAccountIds
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class AdapterManager(
    private val walletManager: IWalletManager,
    private val adapterFactory: AdapterFactory,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val solanaKitManager: SolanaKitManager,
    private val tronKitManager: TronKitManager,
    private val tonKitManager: TonKitManager,
    private val moneroKitManager: MoneroKitManager,
    private val stellarKitManager: StellarKitManager,
    private val pendingBalanceCalculator: PendingBalanceCalculator,
    private val fallbackAddressProvider: FallbackAddressProvider,
    private val offlineModeManager: OfflineModeManager,
    dispatcherProvider: DispatcherProvider
) : IAdapterManager, HandlerThread("A") {

    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(dispatcherProvider.io + SupervisorJob())

    private val adaptersReadySubject = PublishSubject.create<Map<Wallet, IAdapter>>()
    private val adaptersMap = ConcurrentHashMap<Wallet, IAdapter>()

    private val _initializationInProgressFlow = MutableStateFlow(true)
    override val initializationInProgressFlow = _initializationInProgressFlow.asStateFlow()

    private val _walletBalanceUpdatedFlow = MutableSharedFlow<Wallet>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val walletBalanceUpdatedFlow: SharedFlow<Wallet> = _walletBalanceUpdatedFlow.asSharedFlow()

    private val balanceSubscriptionJobs = ConcurrentHashMap<Wallet, Job>()

    override val adaptersReadyObservable: Flowable<Map<Wallet, IAdapter>> =
        adaptersReadySubject.toFlowable(BackpressureStrategy.BUFFER)

    private val initRequests = MutableSharedFlow<List<Wallet>>(extraBufferCapacity = 1)

    init {
        start()

        coroutineScope.launch {
            initRequests
                .conflate()
                .collectLatest { wallets ->
                    mutex.withLock {
                        initAdaptersInternal(wallets)
                    }
                }
        }
    }

    override fun startAdapterManager() {
        coroutineScope.launch {
            walletManager.activeWalletsFlow.collect { wallets ->
                requestInitAdapters(wallets)
            }
        }
        coroutineScope.launch {
            btcBlockchainManager.restoreModeUpdatedObservable.asFlow().collect {
                reinitAdapters(it)
            }
        }
        coroutineScope.launch {
            solanaKitManager.kitStoppedObservable.asFlow().collect {
                reinitAdapters(BlockchainType.Solana)
            }
        }
        coroutineScope.launch {
            moneroKitManager.kitStoppedObservable.asFlow().collect {
                reinitAdapters(BlockchainType.Monero)
            }
        }
        for (blockchain in evmBlockchainManager.allBlockchains) {
            coroutineScope.launch {
                evmBlockchainManager.getEvmKitManager(blockchain.type).evmKitUpdatedObservable.asFlow()
                    .collect {
                        reinitAdapters(blockchain.type)
                    }
            }
        }
    }

    private suspend fun reinitAdapters(blockchainType: BlockchainType) {
        val removedAdapters = mutex.withLock {
            adaptersMap.keys
                .filter { it.token.blockchainType == blockchainType }
                .mapNotNull { wallet ->
                    val adapter = adaptersMap.remove(wallet) ?: return@mapNotNull null
                    cancelBalanceSubscription(wallet)
                    wallet to adapter
                }
                .also {
                    if (it.isNotEmpty()) {
                        adaptersReadySubject.onNext(HashMap(adaptersMap))
                    }
                }
        }
        if (removedAdapters.isEmpty()) return

        removedAdapters.forEach { (wallet, adapter) ->
            adapter.stop()
            adapterFactory.unlinkAdapter(wallet)
        }
        requestInitAdapters(walletManager.activeWallets)
    }

    override suspend fun refresh() {
        val pausedChains = pausedChains()

        coroutineScope.launch {
            adaptersMap.forEach { (wallet, adapter) ->
                if (!wallet.isNetworkPaused()) adapter.refresh()
            }
        }

        for (blockchain in evmBlockchainManager.allBlockchains) {
            if (blockchain.type in pausedChains) continue
            evmBlockchainManager.getEvmKitManager(blockchain.type).refresh()
        }

        if (BlockchainType.Solana !in pausedChains) solanaKitManager.solanaKitWrapper?.solanaKit?.refresh()
        if (BlockchainType.Tron !in pausedChains) tronKitManager.tronKitWrapper?.tronKit?.refresh()
        if (BlockchainType.Ton !in pausedChains) tonKitManager.tonKitWrapper?.tonKit?.refresh()
        if (BlockchainType.Monero !in pausedChains) moneroKitManager.moneroKitWrapper?.refresh()
        if (BlockchainType.Stellar !in pausedChains) stellarKitManager.stellarKitWrapper?.stellarKit?.refresh()
    }

    /** A chain counts as paused only when a wallet of the active account actually holds it paused. */
    private fun pausedChains(): Set<BlockchainType> =
        adaptersMap.keys.filter { it.isNetworkPaused() }.mapTo(mutableSetOf()) { it.token.blockchainType }

    private fun Wallet.isNetworkPaused(): Boolean =
        offlineModeManager.isNetworkPaused(account.id, token.blockchainType)

    private fun requestInitAdapters(wallets: List<Wallet>) {
        initRequests.tryEmit(wallets)
    }

    private suspend fun initAdaptersInternal(wallets: List<Wallet>) {
        val currentAdapters = adaptersMap.toMutableMap()
        adaptersMap.clear()
        _initializationInProgressFlow.value = true
        val previousLitecoinMwebAccounts = currentAdapters.keys.litecoinMwebAccountIds()
        val activeLitecoinMwebAccounts = wallets.litecoinMwebAccountIds()

        // Only one account is active at a time
        val activeAccountId = wallets.firstOrNull()?.account?.id
        val previousAccountId = currentAdapters.keys.firstOrNull()?.account?.id

        if (activeAccountId != null) {
            pendingBalanceCalculator.startObserving(activeAccountId)
        }

        // Separate reusable adapters from ones that need creation
        val reusable = mutableMapOf<Wallet, IAdapter>()
        val toCreate = mutableListOf<Wallet>()

        wallets.forEach { wallet ->
            val existing = currentAdapters[wallet]
            if (existing == null || wallet.needsLitecoinMwebRecreate(
                    previousLitecoinMwebAccounts,
                    activeLitecoinMwebAccounts
                )
            ) {
                toCreate.add(wallet)
            } else {
                currentAdapters.remove(wallet)
                reusable[wallet] = existing
            }
        }

        // Stop old adapters that won't be reused BEFORE creating new ones.
        // This is critical for Zcash: its SDK forbids creating a new Synchronizer
        // while another one with the same alias is still active.
        currentAdapters.forEach { (wallet, adapter) ->
            cancelBalanceSubscription(wallet)
            adapter.stop()
            coroutineScope.launch {
                adapterFactory.unlinkAdapter(wallet)
            }
        }

        // Add reusable adapters immediately and subscribe to balance updates
        adaptersMap.putAll(reusable)
        reusable.forEach { (wallet, adapter) ->
            (adapter as? IBalanceAdapter)?.let { subscribeToBalanceUpdates(wallet, it) }
        }

        // Emit immediately so transaction loading can start with reusable adapters
        if (reusable.isNotEmpty()) {
            adaptersReadySubject.onNext(HashMap(adaptersMap))
        }

        // Create new adapters in parallel with two-phase emission:
        // Phase 1: early batch after EARLY_BATCH_DELAY_MS — captures fast adapters
        // Phase 2: final emission after all adapters complete
        if (toCreate.isNotEmpty()) {
            supervisorScope {
                val jobs = toCreate.map { wallet ->
                    launch {
                        try {
                            adapterFactory.getAdapterOrNull(wallet, activeLitecoinMwebAccounts)?.let {
                                startAdapter(wallet, it)
                            }
                        } catch (ex: Exception) {
                            Timber.e(ex, "Can't get adapter")
                        }
                    }
                }

                // Early batch: emit whatever is ready after a short delay
                val earlyBatchJob = launch {
                    delay(EARLY_BATCH_DELAY_MS)
                    if (jobs.any { it.isActive }) {
                        adaptersReadySubject.onNext(HashMap(adaptersMap))
                    }
                }

                jobs.joinAll()
                earlyBatchJob.cancel()
            }

            // Final emission with all adapters
            adaptersReadySubject.onNext(HashMap(adaptersMap))
        }

        // Stop observing if account changed
        if (previousAccountId != null && previousAccountId != activeAccountId) {
            pendingBalanceCalculator.stopObserving(previousAccountId)
        }
        _initializationInProgressFlow.value = false
    }

    private fun Wallet.needsLitecoinMwebRecreate(
        previousLitecoinMwebAccounts: Set<String>,
        activeLitecoinMwebAccounts: Set<String>,
    ): Boolean {
        if (token.blockchainType != BlockchainType.Litecoin) return false
        if (token.type !is TokenType.Derived) return false

        return (account.id in previousLitecoinMwebAccounts) != (account.id in activeLitecoinMwebAccounts)
    }

    /**
     * Partial refresh of adapters
     * For the given list of wallets do:
     * - remove corresponding adapters from adaptersMap and stop them
     * - create new adapters, start and add them to adaptersMap
     * - trigger adaptersReadySubject
     */
    override fun refreshAdapters(wallets: List<Wallet>) {
        coroutineScope.launch {
            mutex.withLock {
                val walletsToRefresh = wallets.filter { adaptersMap.containsKey(it) }
                val activeLitecoinMwebAccounts = walletManager.activeWallets.litecoinMwebAccountIds()

                // remove and stop adapters
                walletsToRefresh.forEach { wallet ->
                    cancelBalanceSubscription(wallet)
                    adaptersMap.remove(wallet)?.let { previousAdapter ->
                        previousAdapter.stop()
                        coroutineScope.launch {
                            adapterFactory.unlinkAdapter(wallet)
                        }
                    }
                }

                // add and start new adapters
                walletsToRefresh.forEach { wallet ->
                    adapterFactory.getAdapterOrNull(wallet, activeLitecoinMwebAccounts)?.let { adapter ->
                        startAdapter(wallet, adapter)
                    }
                }

                adaptersReadySubject.onNext(HashMap(adaptersMap))
            }
        }
    }

    /**
     * Rescans every Zcash wallet of [accountId] from a new birthday height. Unlike
     * [refreshAdapters], the whole operation (stop, unlink, [clearData], reconstruct, start)
     * is awaited inside the single [mutex] hold — no fire-and-forget unlink — so no adapter
     * init can observe the account half torn down.
     *
     * If [clearData] throws, the group is reconstructed unchanged and the exception is
     * rethrown. If reconstruction itself fails for part of the group (adapter creation never
     * throws, it returns null), the reduced map is published, a full adapter re-init is
     * requested as a self-heal, and [ZcashRescanException] is thrown.
     */
    suspend fun rescanZcashAccount(accountId: String, clearData: suspend () -> Unit) {
        mutex.withLock {
            val group = walletManager.activeWallets.filter {
                it.account.id == accountId && it.token.blockchainType == BlockchainType.Zcash
            }
            if (group.isEmpty()) return@withLock

            stopAndUnlinkGroup(group)

            try {
                clearData()
            } catch (e: Exception) {
                reconstructAndStartGroup(group)
                throw e
            }

            val reconstructedCount = reconstructAndStartGroup(group)
            if (reconstructedCount != group.size) {
                requestInitAdapters(walletManager.activeWallets)
                throw ZcashRescanException("Failed to reconstruct all Zcash wallets for account $accountId")
            }
        }
    }

    private suspend fun stopAndUnlinkGroup(group: List<Wallet>) {
        group.forEach { wallet ->
            cancelBalanceSubscription(wallet)
            adaptersMap[wallet]?.stop()
            adapterFactory.unlinkAdapter(wallet)
            adaptersMap.remove(wallet)
        }
    }

    private suspend fun reconstructAndStartGroup(group: List<Wallet>): Int {
        // Zcash-only group: getAdapterOrNull's activeLitecoinMwebAccounts argument is read only by
        // the factory's Litecoin branch, so it is left at its default here (the factory derives it
        // itself if ever needed).
        val constructed = group.mapNotNull { wallet ->
            adapterFactory.getAdapterOrNull(wallet)?.let { wallet to it }
        }
        constructed.forEach { (wallet, adapter) ->
            startAdapter(wallet, adapter)
        }
        adaptersReadySubject.onNext(HashMap(adaptersMap))
        return constructed.size
    }

    override fun refreshByWallet(wallet: Wallet) {
        if (wallet.isNetworkPaused()) return

        val blockchain = evmBlockchainManager.getBlockchain(wallet.token)

        if (blockchain != null) {
            evmBlockchainManager.getEvmKitManager(blockchain.type).evmKitWrapper?.evmKit?.refresh()
        } else {
            coroutineScope.launch {
                adaptersMap[wallet]?.refresh()
            }
        }
    }

    override suspend fun stopAdapters(accountIds: List<String>) {
        val accountIdSet = accountIds.toSet()
        stopMatchingAdapters { it.account.id in accountIdSet }
    }

    override suspend fun stopAdapters(accountIds: List<String>, blockchainType: BlockchainType) {
        val accountIdSet = accountIds.toSet()
        stopMatchingAdapters {
            it.account.id in accountIdSet && it.token.blockchainType == blockchainType
        }
    }

    private suspend fun stopMatchingAdapters(matchingWallet: (Wallet) -> Boolean) {
        val removedAdapters = mutex.withLock {
            adaptersMap.keys
                .filter(matchingWallet)
                .mapNotNull { wallet ->
                    adaptersMap.remove(wallet)?.let { adapter ->
                        cancelBalanceSubscription(wallet)
                        wallet to adapter
                    }
                }
                .also {
                    if (it.isNotEmpty()) {
                        adaptersReadySubject.onNext(HashMap(adaptersMap))
                    }
                }
        }

        removedAdapters.forEach { (wallet, adapter) ->
            adapter.stop()
            adapterFactory.unlinkAdapter(wallet)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> awaitAdapterForWallet(wallet: Wallet, timeoutMs: Long): T? {
        (adaptersMap[wallet] as? T)?.let { return it }

        return withTimeoutOrNull(timeoutMs) {
            merge(
                initializationInProgressFlow.filter { !it }.map { adaptersMap },
                adaptersReadyObservable.asFlow()
            )
                .mapNotNull { it[wallet] as? T }
                .first()
        } ?: adaptersMap[wallet] as? T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getAdapterForWallet(wallet: Wallet): T? {
        return adaptersMap[wallet] as? T
    }

    override fun getAdapterForWalletOld(wallet: Wallet): IAdapter? {
        return adaptersMap[wallet]
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getAdapterForToken(token: Token): T? {
        return walletManager.activeWallets.firstOrNull { it.token == token }
            ?.let(::getAdapterForWallet)
    }

    override fun getBalanceAdapterForWallet(wallet: Wallet): IBalanceAdapter? {
        return adaptersMap[wallet]?.let { it as? IBalanceAdapter }
    }

    override fun getReceiveAdapterForWallet(wallet: Wallet): IReceiveAdapter? {
        return adaptersMap[wallet]?.let { it as? IReceiveAdapter }
    }

    override fun getAdjustedBalanceData(wallet: Wallet): BalanceData? {
        val adapter = getBalanceAdapterForWallet(wallet) ?: return null
        val rawBalance = adapter.balanceData
        return pendingBalanceCalculator.adjustBalance(wallet, rawBalance)
    }

    override fun getAdjustedBalanceDataForToken(token: Token): BalanceData? {
        val wallet = walletManager.activeWallets.firstOrNull { it.token == token } ?: return null
        return getAdjustedBalanceData(wallet)
    }

    override suspend fun getReceiveAddressForWallet(wallet: Wallet): String? {
        getReceiveAdapterForWallet(wallet)?.receiveAddress?.let { return it }
        return fallbackAddressProvider.getAddress(wallet)
    }

    /**
     * Registers the adapter only once it has started, so a failed start is never published.
     * The sync state is captured beforehand, so a kit that syncs instantly still looks like a transition.
     */
    private fun startAdapter(wallet: Wallet, adapter: IAdapter) {
        val stateBeforeStart = (adapter as? IBalanceAdapter)?.balanceState
        if (wallet.isNetworkPaused()) {
            adapter.attachLocalData()
            // Kits that reach the network from a local read (Bitcoin resolves input addresses while
            // serving history) only stay offline once they are told the network is paused.
            adapter.pauseNetwork()
        } else {
            adapter.start()
        }
        adaptersMap[wallet] = adapter
        (adapter as? IBalanceAdapter)?.let { subscribeToBalanceUpdates(wallet, it, stateBeforeStart) }
    }

    private fun subscribeToBalanceUpdates(
        wallet: Wallet,
        adapter: IBalanceAdapter,
        stateBeforeStart: AdapterState? = null,
    ) {
        balanceSubscriptionJobs[wallet]?.cancel()
        offlineModeManager.onSubscribed(wallet, adapter, stateBeforeStart ?: adapter.balanceState)
        balanceSubscriptionJobs[wallet] = coroutineScope.launch {
            launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.balanceStateUpdatedFlow.collect {
                    offlineModeManager.onBalanceState(wallet, adapter, adapter.balanceState)
                }
            }
            offlineModeManager.onBalanceState(wallet, adapter, adapter.balanceState)
            offlineModeManager.seedLastSynced(
                wallet = wallet,
                lastBlockTimestampSec = (adapter as? ITransactionsAdapter)?.lastBlockInfo?.timestamp,
            )
            merge(
                adapter.balanceUpdatedFlow,
                adapter.balanceStateUpdatedFlow,
                adapter.transactionsSyncStateUpdatedFlow,
            ).collectLatest {
                _walletBalanceUpdatedFlow.emit(wallet)
            }
        }
    }

    private fun cancelBalanceSubscription(wallet: Wallet) {
        balanceSubscriptionJobs.remove(wallet)?.cancel()
        offlineModeManager.onAdapterGone(wallet)
    }

    companion object {
        private const val EARLY_BATCH_DELAY_MS = 2000L
    }
}

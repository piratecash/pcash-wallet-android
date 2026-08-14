package cash.p.terminal.core.adapters.zcash

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.util.Log
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.SignedOfflineZcashTransaction
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.isZcashAlreadyCommittedToBestChainError
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.OneTimeReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.CloseableSynchronizer
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.block.processor.CompactBlockProcessor
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountCreateSetup
import cash.z.ecc.android.sdk.model.AccountImportSetup
import cash.z.ecc.android.sdk.model.AccountPurpose
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.PercentDecimal
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.SignedRawZcashTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.math.max

class ZcashAdapter(
    context: Context,
    private val wallet: Wallet,
    private val restoreSettings: RestoreSettings,
    private val addressSpecTyped: AddressSpecType?,
    private val localStorage: ILocalStorage,
    private val backgroundManager: BackgroundManager,
    private val singleUseAddressManager: ZcashSingleUseAddressManager,
    private val dispatcherProvider: DispatcherProvider,
    private val restartBaseDelayMs: Long = 15_000,
    private val restartMaxDelayMs: Long = 120_000,
) : IAdapter, IBalanceAdapter, IReceiveAdapter, ITransactionsAdapter, ISendZcashAdapter,
    OneTimeReceiveAdapter {
    private var accountBirthday = 0L
    private val existingWallet = localStorage.zcashAccountIds.contains(wallet.account.id)
    private val confirmationsThreshold = 10
    private val network: ZcashNetwork = ZcashNetwork.Mainnet
    private val lightWalletEndpoint =
        LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)

    private val recovering = AtomicBoolean(false)
    private val corruptionRecovery = AtomicBoolean(false)
    private val pollingSessionCount = AtomicInteger(0)

    @Volatile
    private var synchronizer: CloseableSynchronizer
    private var transactionsProvider: ZcashTransactionsProvider
    private val clearZCashWalletDataUseCase: ClearZCashWalletDataUseCase by inject(
        ClearZCashWalletDataUseCase::class.java
    )
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager by inject(
        BackgroundKeepAliveManager::class.java
    )

    private val adapterStateUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val lastBlockUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val balanceUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()

    private val accountType =
        (wallet.account.type as? AccountType.Mnemonic)
            ?: (wallet.account.type as? AccountType.ZCashUfvKey)
            ?: (wallet.account.type as? AccountType.TrezorDevice)
            ?: throw UnsupportedAccountException()

    private val seed = (accountType as? AccountType.Mnemonic)?.seed ?: ByteArray(0)

    private var zcashAccount: Account? = null

    override var receiveAddress: String

    private var startJob: Job? = null
    private var statusJob: Job? = null
    private var restartJob: Job? = null
    private var restartAttempt = 0
    private var subscriberScope: CoroutineScope? = null
    override val isMainNet: Boolean = true
    private val scope = CoroutineScope(dispatcherProvider.io)

    private var balanceCheckJob: Job? = null
    private val balanceCheckMutex = Mutex()

    val poolName: String
        get() = poolLabel(addressSpecTyped)

    private var importUfvkError : Throwable? = null

    private val feeLock = Any()
    private var feeJob: Job? = null
    private var lastFeeSnapshot: AccountBalance? = null
    private var feeGeneration = 0L

    @Volatile
    private var ironwoodMigrationProposal: Proposal? = null

    private val migrationTxKeyPrefix = "${wallet.account.id}:"

    companion object {
        private const val DECIMAL_COUNT = 8

        // NU6.3 (Ironwood) activation heights
        private const val IRONWOOD_ACTIVATION_HEIGHT_MAINNET = 3_428_143L
        private const val IRONWOOD_ACTIVATION_HEIGHT_TESTNET = 4_134_000L

        // logcat tag for the stuck-pending diagnostic; filter with `adb logcat -s ZcashDiag`.
        private const val DIAG_TAG = "ZcashDiag"
        private val DATABASE_CORRUPTION_MESSAGES = listOf(
            "database disk image is malformed",
            "file is not a database",
            "database is corrupt",
        )

        val MINERS_FEE = ZcashSdk.MINERS_FEE.convertZatoshiToZec(DECIMAL_COUNT)

        // The fee probe steps down by MINERS_FEE (10,000 zat) per attempt. With funds spread
        // over several pools ZIP-317 charges per bundle and per input, so a near-max send
        // easily exceeds the former 4 steps (40,000 zat). 20 attempts cover fees up to
        // 200,000 zat; the probe only runs to the end in the rare case where the whole
        // balance cannot be sent at all.
        private const val FEE_PROBE_ATTEMPTS = 20

        // Every account has its own adapters, but they all update the same stored id set.
        private val migrationIdsMutex = Mutex()
    }

    init {
        val walletInitMode = resolveWalletInitMode()
        val birthday = resolveBirthday(context)

        birthday?.value?.let {
            accountBirthday = it
        }

        val setup = if (!requiresUfvkImport()) {
            AccountCreateSetup(
                seed = FirstClassByteArray(seed),
                accountName = wallet.account.name,
                keySource = null
            )
        } else {
            null
        }

        synchronizer = Synchronizer.newBlocking(
            context = context,
            zcashNetwork = network,
            alias = clearZCashWalletDataUseCase.getValidAliasFromAccountId(
                wallet.account.id,
                addressSpecTyped
            ),
            lightWalletEndpoint = lightWalletEndpoint,
            birthday = birthday,
            walletInitMode = walletInitMode,
            setup = setup,
            isTorEnabled = localStorage.torEnabled,
            isExchangeRateEnabled = false
        )

        runBlocking { importWatchAccountIfNeeded() }

        zcashAccount = runBlocking { tryOrNull { getFirstAccount() } }
        receiveAddress = runBlocking { getReceiveAddressOrEmpty() }
        transactionsProvider =
            ZcashTransactionsProvider(
                synchronizer = synchronizer as SdkSynchronizer
            )
        synchronizer.onProcessorErrorHandler = ::onProcessorError
        synchronizer.onCriticalErrorHandler = ::onCriticalError
        synchronizer.onChainErrorHandler = ::onChainError

        subscribeToEvents()
    }

    override suspend fun generateOneTimeAddress(): String? {
        val sdk = synchronizer as? SdkSynchronizer ?: return null

        return try {
            val singleUseAddress = sdk.getSingleUseTransparentAddress(getFirstAccount().accountUuid)
            singleUseAddressManager.saveNewAddress(singleUseAddress.address)
            singleUseAddress.address
        } catch (error: Exception) {
            Timber.w(error, "Failed to obtain single-use transparent address")
            singleUseAddressManager.getNextAddress()
        }
    }

    private fun requiresUfvkImport(): Boolean {
        return wallet.account.type is AccountType.ZCashUfvKey
            || wallet.account.type is AccountType.TrezorDevice
    }

    private fun resolveWalletInitMode(): WalletInitMode {
        return if (existingWallet || requiresUfvkImport()) {
            WalletInitMode.ExistingWallet
        } else when (wallet.account.origin) {
            AccountOrigin.Created -> WalletInitMode.NewWallet
            AccountOrigin.Restored -> WalletInitMode.RestoreWallet
        }
    }

    private fun resolveBirthday(context: Context): BlockHeight? {
        return when (wallet.account.origin) {
            AccountOrigin.Created -> runBlocking { BlockHeight.ofLatestCheckpoint(context, network) }
            AccountOrigin.Restored -> restoreSettings.birthdayHeight
                ?.let { height -> max(network.saplingActivationHeight.value, height) }
                ?.let { BlockHeight.new(it) }
        }
    }

    private suspend fun importWatchAccountIfNeeded() {
        if (!requiresUfvkImport()) return
        val key = wallet.zcashWatchOnlyUfvk() ?: return
        try {
            (synchronizer as Synchronizer).importAccountByUfvk(
                AccountImportSetup(
                    accountName = wallet.account.name,
                    keySource = null,
                    purpose = AccountPurpose.ViewOnly,
                    ufvk = UnifiedFullViewingKey(key)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to import watch-only ZCash account")
            importUfvkError = e
        }
    }

    private suspend fun getReceiveAddressOrEmpty(): String {
        return try {
            val account = getFirstAccount()
            addressSpecTyped.selectZcashReceiver(
                sapling = { synchronizer.getSaplingAddress(account) },
                transparent = { synchronizer.getTransparentAddress(account) },
                unified = { synchronizer.getUnifiedAddress(account) },
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get receive address")
            ""
        }
    }

    private fun subscribeToEvents() {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                when (state) {
                    BackgroundManagerState.EnterForeground -> {
                        // Cancel a pending self-heal restart so it doesn't race with this one.
                        resetRestart()
                        start()
                    }
                    BackgroundManagerState.EnterBackground -> {
                        if (!hasActiveBackgroundSession()) {
                            pauseSynchronizer()
                        } else {
                            Timber.tag("TxPoller").d("ZcashAdapter staying alive")
                        }
                    }
                    BackgroundManagerState.Unknown,
                    BackgroundManagerState.AllActivitiesDestroyed -> {}
                }
            }
        }
        subscribeToStatus()
    }

    suspend fun getFirstAccount(): Account {
        return zcashAccount ?: synchronizer.getAccounts().firstOrNull() ?: throw Exception("No account found")
    }

    private var syncState: AdapterState = AdapterState.Connecting
        set(value) {
            if (value != field) {
                field = value
                adapterStateUpdatedSubject.onNext(Unit)
            }
        }

    private var lastDownloadProgressDecimal: Float = 0f
    private var lastNetworkHeight: Long? = null

    private suspend fun createNewSynchronizer() {
        val isRecovery = corruptionRecovery.get()
        val walletInitMode = if (isRecovery) {
            WalletInitMode.RestoreWallet
        } else {
            resolveWalletInitMode()
        }

        val birthday = if (isRecovery) {
            restoreSettings.birthdayHeight
                ?.let { max(network.saplingActivationHeight.value, it) }
                ?.let { BlockHeight.new(it) }
        } else {
            resolveBirthday(App.instance)
        }

        birthday?.value?.let {
            accountBirthday = it
        }
        try {
            val setup = if (!requiresUfvkImport()) {
                AccountCreateSetup(
                    seed = FirstClassByteArray(seed),
                    accountName = wallet.account.name,
                    keySource = null
                )
            } else {
                null
            }
            synchronizer = Synchronizer.new(
                context = App.instance,
                zcashNetwork = network,
                alias = clearZCashWalletDataUseCase.getValidAliasFromAccountId(
                    wallet.account.id,
                    addressSpecTyped
                ),
                lightWalletEndpoint = lightWalletEndpoint,
                birthday = birthday,
                walletInitMode = walletInitMode,
                setup = setup,
                isTorEnabled = localStorage.torEnabled,
                isExchangeRateEnabled = false
            )

            importWatchAccountIfNeeded()

        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // To prevent crash with synchronizer creation in some situations
            // when java.lang.IllegalStateException: Another synchronizer with SynchronizerKey
            Timber.d("Synchronizer creation failed: ${ex.message}")
            closeSynchronizer()
            delay(3000)
            createNewSynchronizer()
            return
        }

        corruptionRecovery.set(false)
        zcashAccount = tryOrNull { getFirstAccount() }
        receiveAddress = getReceiveAddressOrEmpty()
        transactionsProvider =
            ZcashTransactionsProvider(
                synchronizer = synchronizer as SdkSynchronizer
            )
        synchronizer.onProcessorErrorHandler = ::onProcessorError
        synchronizer.onCriticalErrorHandler = ::onCriticalError
        synchronizer.onChainErrorHandler = ::onChainError
    }

    private fun subscribeToStatus() {
        statusJob?.cancel()
        statusJob = scope.launch {
            synchronizer.status.collect {
                if (it == Synchronizer.Status.SYNCED) {
                    scheduleFeeRecalculation()
                }
            }
        }
    }

    override fun start() {
        // Corruption recovery owns the whole lifecycle: it closes, erases, and recreates the
        // synchronizer itself, so every other restart trigger (foreground, polling, self-heal)
        // must stay out of the way while it is in flight.
        if (recovering.get()) return
        importUfvkError?.let {
            syncState = AdapterState.NotSynced(it)
            return
        }
        startJob?.cancel()
        startJob = scope.launch {
            try {
                startSynchronizer()
            } catch (e: IllegalStateException) {
                // Previous synchronizer still closing, wait and retry
                Timber.d("Synchronizer conflict, retrying after delay: ${e.message}")
                delay(1000)
                startSynchronizer()
            }
        }
    }

    private suspend fun startSynchronizer() {
        val sdk = synchronizer as SdkSynchronizer
        if (sdk.status.value == Synchronizer.Status.STOPPED || !sdk.coroutineScope.isActive) {
            createNewSynchronizer()
        }
        subscribe(synchronizer as SdkSynchronizer)
        subscribeToStatus()
        if (!existingWallet) {
            localStorage.zcashAccountIds += wallet.account.id
        }
    }

    private fun closeSynchronizer() {
        balanceCheckJob?.cancel()
        statusJob?.cancel()
        subscriberScope?.cancel()
        subscriberScope = null
        tryOrNull { synchronizer.close() }
    }

    private fun pauseSynchronizer() {
        startJob?.cancel()
        closeSynchronizer()
    }

    override fun stop() {
        scope.cancel()
        closeSynchronizer()
    }

    override suspend fun refresh() = withContext(dispatcherProvider.io) {
        with(synchronizer as SdkSynchronizer) {
            refreshAllBalances()
            refreshTransactions()
        }
    }

    fun startForPolling() {
        pollingSessionCount.onPollingStarted {
            start()
        }
    }

    fun stopForPolling() {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            pauseSynchronizer()
        }
    }

    override val debugInfo: String
        get() = ""

    override val balanceState: AdapterState
        get() = syncState

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val balanceData: BalanceData
        get() = walletBalance.toBalanceData(DECIMAL_COUNT)

    override val statusInfo: Map<String, Any>
        get() {
            val statusInfo = LinkedHashMap<String, Any>()
            statusInfo["Last Block Info"] = lastBlockInfo ?: ""
            statusInfo["Sync State"] = safeSyncStateLabel(syncState)
            statusInfo["Birthday Height"] = accountBirthday
            return statusInfo
        }

    private val accountBalance: AccountBalance?
        get() = synchronizer.walletBalances.value?.get(zcashAccount?.accountUuid)

    private val walletBalance: WalletBalance
        get() = poolWalletBalanceOrNull(synchronizer)
            ?: WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0))

    /**
     * The pool's balance, or `null` when the SDK has not published any balances for this account yet.
     * `null` is kept distinct from a real zero so the diagnostic never reports "not loaded" as empty;
     * the UI-facing [walletBalance] still substitutes zero as before.
     */
    private fun poolWalletBalanceOrNull(sync: Synchronizer): WalletBalance? {
        val accountBalance = sync.walletBalances.value?.get(zcashAccount?.accountUuid) ?: return null
        return when (addressSpecTyped) {
            null,
            AddressSpecType.Shielded -> accountBalance.sapling

            AddressSpecType.Transparent -> WalletBalance(
                available = accountBalance.unshielded,
                changePending = Zatoshi(0),
                valuePending = Zatoshi(0)
            )

            // After NU6.3 activation the turnstile forbids adding value to Orchard, so change
            // and payments to Orchard recipients are built in an Ironwood bundle. A unified
            // address therefore holds funds in both pools and its balance is their sum.
            AddressSpecType.Unified -> WalletBalance(
                available = accountBalance.orchard.available + accountBalance.ironwood.available,
                changePending = accountBalance.orchard.changePending +
                    accountBalance.ironwood.changePending,
                valuePending = accountBalance.orchard.valuePending + accountBalance.ironwood.valuePending
            )
        }
    }

    override val balanceUpdatedFlow: Flow<Unit>
        get() = balanceUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    private fun readDiagSnapshot(): ZcashDiagSnapshot {
        // Capture one synchronizer generation so processor and balance fields never mix generations.
        val sync = synchronizer as SdkSynchronizer
        val processor = sync.processor
        val processorInfo = processor.processorInfo.value
        val range = processorInfo.overallSyncRange
        val rangeState = when {
            range == null -> SyncRangeState.Unknown
            range.isEmpty() -> SyncRangeState.Empty
            else -> SyncRangeState.NonEmpty
        }
        val balance = poolWalletBalanceOrNull(sync)
        return ZcashDiagSnapshot(
            pool = poolName,
            syncStateDiscriminator = syncState::class.simpleName ?: "Unknown",
            chainTipHeight = processor.networkHeight.value?.value,
            fullyScannedHeight = processor.fullyScannedHeight.value?.value,
            scanProgressPercent = processor.scanProgress.value.toPercentage(),
            recoveryProgressPercent = processor.recoveryProgress.value?.toPercentage(),
            overallSyncRangeState = rangeState,
            overallSyncRangeStart = range?.start?.value,
            overallSyncRangeEnd = range?.endInclusive?.value,
            firstUnenhancedHeight = processorInfo.firstUnenhancedHeight?.value,
            available = balance?.available?.convertZatoshiToZec(DECIMAL_COUNT),
            changePending = balance?.changePending?.convertZatoshiToZec(DECIMAL_COUNT),
            valuePending = balance?.valuePending?.convertZatoshiToZec(DECIMAL_COUNT),
        )
    }

    // Privacy-safe diagnostic line for the stuck-pending investigation; only coarse
    // booleans/buckets and public block heights are logged, never amounts/keys/addresses.
    private fun logDiag() {
        // Best-effort: a diagnostic must never propagate into the flow collectors
        // (onStatus/onProcessorInfo/onBalance) and kill sync or self-heal restart.
        tryOrNull { Log.d(DIAG_TAG, diagFields(readDiagSnapshot()).toString()) }
    }

    override val explorerTitle: String
        get() = "blockchair.com"

    override val transactionsState: AdapterState
        get() = syncState

    override val transactionsStateUpdatedFlowable: Flowable<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val lastBlockInfo: LastBlockInfo?
        get() = synchronizer.latestHeight?.value?.toInt()?.let { LastBlockInfo(it) }

    override val lastBlockUpdatedFlowable: Flowable<Unit>
        get() = lastBlockUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)


    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> {
        val fromParams = from?.let {
            val transactionHash = it.transactionHash.hexToByteArray().reversedArray()
            Triple(transactionHash, it.timestamp, it.transactionIndex)
        }
        return transactionsProvider.getTransactions(
            fromParams,
            transactionType,
            address,
            limit
        ).map {
            getTransactionRecord(it)
        }
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> {
        return transactionsProvider.getNewTransactionsFlowable(transactionType, address)
            .map { transactions ->
                transactions.map { getTransactionRecord(it) }
            }
    }

    override fun getTransactionUrl(transactionHash: String): String =
        "https://blockchair.com/zcash/transaction/$transactionHash"

    override val maxSpendableBalance: BigDecimal
        get() {
            return with(walletBalance) {
                val defaultFee = fee.value.convertZecToZatoshi()
                if (available <= defaultFee) {
                    BigDecimal.ZERO
                } else {
                    available.minus(defaultFee)
                        .convertZatoshiToZec(DECIMAL_COUNT)
                }
            }
        }

    private val _fee: MutableStateFlow<BigDecimal> = MutableStateFlow(MINERS_FEE)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    /**
     * Restarts the fee calculation whenever the account balance changes.
     *
     * The marker is the whole [AccountBalance] rather than `available` alone: under ZIP-317 the
     * fee depends on which pools are involved, and after NU6.3 activation funds can move from
     * Orchard to Ironwood without changing the total. The snapshot is read under the lock
     * because this runs both from `onBalance` (main dispatcher) and from the status collector
     * (IO): otherwise an older call could overwrite the marker and cancel the calculation
     * started for the fresher balance.
     *
     * While the published fee is still the default one the snapshot is not enough to conclude
     * the fee is current — ZIP-317 also depends on the proposal target height, which changes at
     * NU6.3 activation without touching any balance field — so the calculation repeats on every
     * trigger until a real fee is known.
     */
    private fun scheduleFeeRecalculation() {
        synchronized(feeLock) {
            val snapshot = accountBalance
            if (snapshot == lastFeeSnapshot && _fee.value != MINERS_FEE) return
            lastFeeSnapshot = snapshot
            val generation = ++feeGeneration
            val available = walletBalance.available
            feeJob?.cancel()
            feeJob = scope.launch {
                val calculated = calculateFee(available)
                synchronized(feeLock) {
                    // The probe may have passed its last cancellation point and returned after a
                    // fresher calculation already published its fee. Ownership is checked by
                    // calculation number rather than by snapshot value: on an A -> B -> A balance
                    // cycle the stale probe would match the snapshot again and publish an
                    // outdated fee.
                    if (feeGeneration != generation) return@launch
                    if (calculated == null) {
                        // The probe found no workable fee — clear the marker so the next balance
                        // tick retries it instead of treating the fee as already calculated.
                        lastFeeSnapshot = null
                    } else {
                        _fee.value = calculated
                    }
                }
            }
        }
    }

    /** Returns the discovered fee, or `null` when the probe failed outright. */
    private suspend fun calculateFee(
        balance: Zatoshi = walletBalance.available,
        tryCounter: Int = FEE_PROBE_ATTEMPTS
    ): BigDecimal? = withContext(dispatcherProvider.io) {
        try {
            if (balance == Zatoshi(0)) {
                return@withContext MINERS_FEE
            }
            synchronizer.proposeTransfer(
                account = getFirstAccount(),
                recipient = AppConfigProvider.donateAddresses[BlockchainType.Zcash]
                    .orEmpty(),
                amount = balance
            ).totalFeeRequired().convertZatoshiToZec(DECIMAL_COUNT)
        } catch (ex: Exception) {
            if (ex is TransactionEncoderException.ProposalFromParametersException && tryCounter > 0) {
                // Not enough money to send with commission
                // Prevent problems with negative Zatoshi
                try {
                    calculateFee(balance - MINERS_FEE.convertZecToZatoshi(), tryCounter - 1)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        }
    }

    override suspend fun validate(address: String): ZCashAddressType {
        if (address == receiveAddress) throw ZcashError.SendToSelfNotAllowed
        return when (synchronizer.validateAddress(address)) {
            is AddressType.Invalid -> throw ZcashError.InvalidAddress
            is AddressType.Transparent -> ZCashAddressType.Transparent
            is AddressType.Shielded -> ZCashAddressType.Shielded
            is AddressType.Tex -> ZCashAddressType.Shielded
            AddressType.Unified -> ZCashAddressType.Unified
        }
    }

    override suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String,
        logger: AppLogger?
    ): FirstClassByteArray {
        logger?.info("call synchronizer.sendToAddress")
        val account = getFirstAccount()
        val proposal = transferProposal(account, amount, address, memo)
        return localizingMissingParams {
            synchronizer.createProposedTransactions(
                proposal = proposal,
                usk = spendingKey(account)
            ).first().txId
        }
    }

    /**
     * Translates the SDK's missing proving parameters failure into a localized message at the
     * single boundary where this adapter talks to the transaction encoder.
     */
    private suspend fun <T> localizingMissingParams(block: suspend () -> T): T =
        try {
            block()
        } catch (_: TransactionEncoderException.MissingParamsException) {
            throw LocalizedException(R.string.send_error_zcash_params_not_downloaded)
        }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineZcashTransaction {
        require(request is OfflineZcashSignRequest) { "OfflineZcashSignRequest is required" }
        val account = getFirstAccount()
        val proposal = transferProposal(
            account = account,
            amount = request.amount,
            address = request.address,
            memo = request.memo,
        )
        val signedTransactions = localizingMissingParams {
            synchronizer.createSignedTransactions(
                proposal = proposal,
                usk = spendingKey(account),
            )
        }
        val signed = signedTransactions.singleOrNull()
            ?: throw UnsupportedException("Zcash offline signing supports exactly one transaction")

        return SignedOfflineZcashTransaction(
            rawHex = signed.raw.byteArray.toRawHexString(),
            txHash = signed.txIdString().canonicalTransactionHash(),
            fee = proposal.totalFeeRequired().convertZatoshiToZec(DECIMAL_COUNT),
        )
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val zcashMetadata = metadata as? OfflineBroadcastMetadata.Zcash
            ?: throw UnsupportedException("Zcash raw broadcast requires P.CASH payload metadata")
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val signedRawTransaction = SignedRawZcashTransaction(
            raw = FirstClassByteArray(normalizedRawHex.hexToByteArray()),
            txId = FirstClassByteArray(zcashMetadata.txHash.hexToByteArray().reversedArray()),
            expiryHeight = null,
        )
        return synchronizer.submitRawTransaction(signedRawTransaction).toZcashRawBroadcastResult()
    }

    private suspend fun transferProposal(
        account: Account,
        amount: BigDecimal,
        address: String,
        memo: String,
    ): Proposal =
        synchronizer.proposeTransfer(
            account = account,
            recipient = address,
            amount = amount.convertZecToZatoshi(),
            memo = memo,
        )

    private suspend fun spendingKey(account: Account): UnifiedSpendingKey {
        val mnemonic = wallet.account.type as? AccountType.Mnemonic
            ?: throw UnsupportedException("Zcash offline signing requires a mnemonic account")
        val accountIndex = account.hdAccountIndex
            ?: throw UnsupportedException("Zcash account index is unavailable")
        return DerivationTool.getInstance()
            .deriveUnifiedSpendingKey(mnemonic.seed, network, accountIndex)
    }

    override suspend fun getOwnAddresses(): List<String> {
        val account = getFirstAccount()
        return listOfNotNull(
            tryOrNull { synchronizer.getSaplingAddress(account) },
            tryOrNull { synchronizer.getUnifiedAddress(account) }
        )
    }

    suspend fun proposeShielding(): FirstClassByteArray = withContext(dispatcherProvider.io) {
        val account = getFirstAccount()
        val proposal = synchronizer.proposeShielding(
            account = account,
            shieldingThreshold = Zatoshi(100000L),
            // Using empty string for memo to clear the default memo prefix value defined in
            // the SDK
            memo = "",
            // Using null will select whichever of the account's trans. receivers has funds
            // to shield
            transparentReceiver = null
        )
        if (proposal == null) {
            throw Throwable("Failed to create proposal")
        }
        localizingMissingParams {
            synchronizer.createProposedTransactions(
                proposal = proposal,
                usk = spendingKey(account)
            ).first().txId
        }
    }

    private val ironwoodActivationHeight: Long
        get() = when (network) {
            ZcashNetwork.Testnet -> IRONWOOD_ACTIVATION_HEIGHT_TESTNET
            else -> IRONWOOD_ACTIVATION_HEIGHT_MAINNET
        }

    /**
     * The Orchard balance that has to be moved to Ironwood, or `null` when migration is not
     * applicable. Orchard and Ironwood are both surfaced by the unified token, so only that
     * adapter can migrate.
     */
    val ironwoodMigrationRequiredBalance: BigDecimal?
        get() {
            if (addressSpecTyped != AddressSpecType.Unified) return null
            if (wallet.account.type !is AccountType.Mnemonic) return null
            if (syncState !is AdapterState.Synced) return null
            val tipHeight = synchronizer.latestHeight?.value ?: return null
            if (tipHeight < ironwoodActivationHeight) return null
            val orchard = accountBalance?.orchard ?: return null
            // The migration proposal is all-or-nothing and fails while any Orchard note is
            // still pending, so offering it before the whole pool is spendable only produces
            // an error the user cannot act on.
            if (orchard.available.value <= 0 || orchard.pending.value > 0) return null
            return orchard.available.convertZatoshiToZec(DECIMAL_COUNT)
        }

    suspend fun proposeIronwoodMigration(): IronwoodMigrationProposal =
        withContext(dispatcherProvider.io) {
            val orchard = checkNotNull(accountBalance?.orchard) { "Orchard balance is not loaded" }
            check(orchard.available.value > 0) { "No spendable Orchard balance" }
            val proposal = synchronizer.proposeOrchardToIronwoodMigration(getFirstAccount())
            ironwoodMigrationProposal = proposal
            val feePaid = proposal.totalFeeRequired()
            IronwoodMigrationProposal(
                amount = Zatoshi((orchard.available.value - feePaid.value).coerceAtLeast(0))
                    .convertZatoshiToZec(DECIMAL_COUNT),
                fee = feePaid.convertZatoshiToZec(DECIMAL_COUNT)
            )
        }

    suspend fun executeIronwoodMigration(): String = withContext(dispatcherProvider.io) {
        // Taken once and never reused, whatever happens next: the proposal names the exact notes
        // to spend, and no failure of the SDK reliably reports whether the transactions were
        // already stored. A repeat attempt has to ask for a fresh proposal instead, which the
        // backend builds with MaxSpendMode::Everything and therefore refuses outright when an
        // earlier attempt has spent part of the pool.
        val proposal = checkNotNull(ironwoodMigrationProposal) { "Migration was not proposed" }
        ironwoodMigrationProposal = null

        val usk = spendingKey(getFirstAccount())
        val results = localizingMissingParams {
            synchronizer
                .createProposedTransactions(proposal = proposal, usk = usk)
                .toList()
        }
        rememberIronwoodMigration(results.map { it.txIdString() })

        results.firstOrNull { it !is TransactionSubmitResult.Success }
            ?.let { error("Migration transaction was not submitted: ${it.txIdString()}") }
        results.firstOrNull()?.txIdString() ?: error("Migration returned no transaction id")
    }

    /**
     * Once the transaction is mined and rescanned from chain its outputs are reported as change
     * and no longer as recipients of this account, so the "transfer to self" heuristic stops
     * matching. Only the recorded transaction id keeps the migration label.
     */
    private suspend fun rememberIronwoodMigration(transactionHashes: List<String>) {
        if (transactionHashes.isEmpty()) return
        migrationIdsMutex.withLock {
            localStorage.zcashIronwoodMigrationTxIds = localStorage.zcashIronwoodMigrationTxIds +
                transactionHashes.map { migrationTxKey(it) }
        }
    }

    /**
     * Read from storage on every check instead of caching: the same transaction is also listed by
     * the sibling Zcash adapters of this account, which never write the migration ids themselves.
     */
    private fun isIronwoodMigration(transactionHashHex: String) =
        migrationTxKey(transactionHashHex) in localStorage.zcashIronwoodMigrationTxIds

    private fun migrationTxKey(transactionHashHex: String) =
        migrationTxKeyPrefix + transactionHashHex.canonicalTransactionHash()

    data class IronwoodMigrationProposal(val amount: BigDecimal, val fee: BigDecimal)

    private fun subscribe(synchronizer: SdkSynchronizer) {
        subscriberScope?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.w(exception, "Zcash synchronizer flow error")
            if (isDatabaseCorruption(exception)) {
                handleDatabaseCorruption(exception)
            }
        }
        val parentJob = synchronizer.coroutineScope.coroutineContext[Job]
        val scope = CoroutineScope(dispatcherProvider.main + SupervisorJob(parentJob) + handler)
        subscriberScope = scope
        synchronizer.allTransactions.safeCollectIn(scope, transactionsProvider::onTransactions)
        synchronizer.status.safeCollectIn(scope, ::onStatus)
        synchronizer.progress.safeCollectIn(scope, ::onDownloadProgress)
        synchronizer.walletBalances.safeCollectIn(scope, ::onBalance)
        synchronizer.processorInfo.safeCollectIn(scope, ::onProcessorInfo)
    }

    private fun <T> Flow<T>.safeCollectIn(scope: CoroutineScope, block: (T) -> Unit) {
        scope.launch {
            catch { e ->
                Timber.e(e, "Zcash flow collection error")
                if (isDatabaseCorruption(e)) {
                    handleDatabaseCorruption(e)
                }
            }.collect { block(it) }
        }
    }

    private fun isDatabaseCorruption(error: Throwable): Boolean {
        return error.causeSequence().any { cause ->
            cause is SQLiteDatabaseCorruptException ||
                    cause.message.isDatabaseCorruptionMessage()
        }
    }

    private fun Throwable.causeSequence(): Sequence<Throwable> {
        return generateSequence(this) { it.cause }
    }

    private fun String?.isDatabaseCorruptionMessage(): Boolean {
        val message = this?.lowercase() ?: return false
        return DATABASE_CORRUPTION_MESSAGES.any(message::contains)
    }

    private fun handleDatabaseCorruption(cause: Throwable) {
        if (!recovering.compareAndSet(false, true)) return
        Timber.e(cause, "Zcash database corruption detected, recovering")
        scope.launch {
            try {
                syncState = AdapterState.NotSynced(Exception("Database corrupted, recovering"))
                try {
                    synchronizer.close()
                } catch (e: Exception) {
                    Timber.w(e, "Error closing corrupted synchronizer")
                }
                eraseWithRetry()
                corruptionRecovery.set(true)
                createNewSynchronizer()
                if (!isActive) {
                    closeSynchronizer()
                    return@launch
                }
                subscribe(synchronizer as SdkSynchronizer)
                subscribeToStatus()
            } catch (e: CancellationException) {
                closeSynchronizer()
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Zcash database corruption recovery failed")
                syncState = AdapterState.NotSynced(Exception("Recovery failed", e))
            } finally {
                recovering.set(false)
            }
        }
    }

    private suspend fun eraseWithRetry() {
        val alias = clearZCashWalletDataUseCase.getValidAliasFromAccountId(
            wallet.account.id, addressSpecTyped
        )
        repeat(3) { attempt ->
            try {
                Synchronizer.erase(App.instance, network, alias)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                if (attempt < 2) {
                    val delayMs = 1000L * (attempt + 1)
                    Timber.d("Synchronizer still active, retrying erase in ${delayMs}ms (attempt ${attempt + 1}/3)")
                    delay(delayMs)
                } else {
                    throw IllegalStateException("Failed to erase corrupted database after 3 attempts", e)
                }
            }
        }
    }

    private fun onProcessorError(error: Throwable?): Boolean {
        Timber.e(error, "Zcash processor error")
        if (error != null && isDatabaseCorruption(error)) {
            handleDatabaseCorruption(error)
            return false
        }
        return true
    }

    private fun onCriticalError(error: Throwable?): Boolean {
        Timber.e(error, "Zcash critical error")
        if (error != null && isDatabaseCorruption(error)) {
            handleDatabaseCorruption(error)
            return false
        }
        return true
    }

    private fun onChainError(errorHeight: BlockHeight, rewindHeight: BlockHeight) = Unit

    // ZEC is intentionally kept running in the background during an active polling session or
    // realtime keep-alive (see EnterBackground above), so self-heal must be allowed in those
    // cases too, not just while the app is in the foreground.
    private fun hasActiveBackgroundSession(): Boolean =
        pollingSessionCount.get() > 0 || backgroundKeepAliveManager.isKeepAlive(BlockchainType.Zcash)

    private fun scheduleRestart() {
        if (recovering.get()) return
        if (!backgroundManager.inForeground && !hasActiveBackgroundSession()) return
        if (restartJob?.isActive == true) return
        val delayMs = zcashRestartDelayFor(restartAttempt, restartBaseDelayMs, restartMaxDelayMs)
        restartAttempt++
        restartJob = scope.launch {
            delay(delayMs)
            // No syncState re-check here: resetRestart() already cancels this job the moment
            // SYNCING/SYNCED is observed, so reaching this point means the restart is still due.
            // (syncState itself is unreliable at this point - subscribe()'s eager resubscription
            // to the progress/processorInfo flows can transiently flip it back to Syncing.)
            if (backgroundManager.inForeground || hasActiveBackgroundSession()) {
                start()
            }
        }
    }

    private fun resetRestart() {
        restartAttempt = 0
        restartJob?.cancel()
        restartJob = null
    }

    private fun onStatus(status: Synchronizer.Status) {
        syncState = when (status) {
            Synchronizer.Status.STOPPED -> AdapterState.NotSynced(Exception("stopped"))
            Synchronizer.Status.DISCONNECTED -> AdapterState.NotSynced(Exception("disconnected"))
            Synchronizer.Status.SYNCING -> if (syncState is AdapterState.Syncing) syncState else AdapterState.Syncing()
            Synchronizer.Status.SYNCED -> AdapterState.Synced
            else -> syncState
        }
        // Self-heal on terminal STOPPED; reset backoff once syncing resumes. DISCONNECTED and
        // PREPARING are left to the SDK's own reconnect loop.
        when (status) {
            Synchronizer.Status.STOPPED -> scheduleRestart()
            Synchronizer.Status.SYNCING,
            Synchronizer.Status.SYNCED -> resetRestart()
            else -> {}
        }
        logDiag()
    }

    private fun startOneTimeAddressBalanceCheck() {
        if (balanceCheckJob?.isActive == true) return

        balanceCheckJob = scope.launch {
            try {
                balanceCheckMutex.withLock {
                    checkTransparentAddressesBalance()
                }
            } finally {
                balanceCheckJob = null
            }
        }
    }

    private fun onDownloadProgress(progress: PercentDecimal) {
        lastDownloadProgressDecimal = progress.decimal
        updateSyncingState()
    }

    private fun onProcessorInfo(processorInfo: CompactBlockProcessor.ProcessorInfo) {
        processorInfo.networkBlockHeight?.value?.let { lastNetworkHeight = it }
        updateSyncingState()
        lastBlockUpdatedSubject.onNext(Unit)
        logDiag()
    }

    // ZCash SDK 2.4 reports `synchronizer.progress` as a fraction over commitment-tree leaves
    // (Sapling+Orchard notes), not blocks. Recovery weight skews heavily to recent history,
    // so the raw decimal stays near 0 for a long time. We expose blocksRemained as the
    // block-equivalent of the SDK's decimal so the UI reads consistently.
    private fun updateSyncingState() {
        if (syncState is AdapterState.Synced) {
            return
        }

        if (lastDownloadProgressDecimal >= 1f) {
            syncState = AdapterState.Syncing(progress = 100.0, blocksRemained = null)
            return
        }

        val effectiveBirthday = max(accountBirthday, network.saplingActivationHeight.value)
        val totalBlocks = lastNetworkHeight?.let { it - effectiveBirthday }?.takeIf { it > 0 }
        val blocksRemained = totalBlocks?.let {
            ((1f - lastDownloadProgressDecimal) * it).toLong().coerceAtLeast(0L)
        }
        val rawPercent = lastDownloadProgressDecimal.toDouble() * 100.0
        val progressPercent = (Math.round(rawPercent * 10000.0) / 10000.0).coerceIn(0.0, 100.0)
        syncState = AdapterState.Syncing(progress = progressPercent, blocksRemained = blocksRemained)
    }

    private fun onBalance(balance: Map<AccountUuid, AccountBalance>?) {
        balance?.get(zcashAccount?.accountUuid)?.sapling?.let {
            balanceUpdatedSubject.onNext(Unit)
        }
        // The pool composition changes at runtime: after NU6.3 activation change arrives in
        // Ironwood and the available balance no longer matches a fee calculated for a single
        // pool. Recalculate on every balance change, not only on the first sync.
        if (syncState is AdapterState.Synced) {
            scheduleFeeRecalculation()
        }
        startOneTimeAddressBalanceCheck()
        logDiag()
    }

    private suspend fun checkTransparentAddressesBalance() = withContext(dispatcherProvider.io) {
        val addresses = singleUseAddressManager.getAddressesForBalanceCheck()
        val sdk = synchronizer as? SdkSynchronizer ?: return@withContext

        addresses.forEach { address ->
            try {
                val balance = sdk.getTransparentBalance(address)
                if (balance.value > 0) {
                    singleUseAddressManager.updateAddressBalance(address, true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                Timber.w(error, "Failed to check balance for t-address: $address")
            }
        }
    }

    private fun getTransactionRecord(transaction: ZcashTransaction): TransactionRecord =
        if (transaction.isIncoming) {
            incomingTransactionRecord(transaction)
        } else {
            outgoingTransactionRecord(transaction)
        }

    private fun incomingTransactionRecord(transaction: ZcashTransaction): TransactionRecord {
        val transactionHashHex = transaction.transactionHash.toReversedHex()
        return BitcoinTransactionRecord(
            token = wallet.token,
            uid = transactionHashHex,
            transactionHash = transactionHashHex,
            transactionIndex = transaction.transactionIndex,
            blockHeight = transaction.minedHeight?.toInt(),
            confirmationsThreshold = confirmationsThreshold,
            timestamp = transaction.timestamp,
            fee = transaction.feePaid?.convertZatoshiToZec(DECIMAL_COUNT)
                ?.let { TransactionValue.CoinValue(wallet.token, it) },
            failed = transaction.failed,
            lockInfo = null,
            conflictingHash = null,
            showRawTransaction = false,
            amount = transaction.value.convertZatoshiToZec(DECIMAL_COUNT),
            from = null,
            to = transaction.toAddress?.let(::listOf),
            changeAddresses = null,
            memo = transaction.memo,
            source = wallet.transactionSource,
            transactionRecordType = TransactionRecordType.BITCOIN_INCOMING
        )
    }

    private fun outgoingTransactionRecord(transaction: ZcashTransaction): TransactionRecord {
        val transactionHashHex = transaction.transactionHash.toReversedHex()
        val isIronwoodMigration = isIronwoodMigration(transactionHashHex)
        // A migration keeps the funds in the wallet, so the moved amount is what was
        // received back rather than the net change of the balance.
        val amount = if (isIronwoodMigration) {
            transaction.totalReceived.convertZatoshiToZec(DECIMAL_COUNT)
        } else {
            transaction.value.convertZatoshiToZec(DECIMAL_COUNT).negate()
        }
        return BitcoinTransactionRecord(
            token = wallet.token,
            uid = transactionHashHex,
            transactionHash = transactionHashHex,
            transactionIndex = transaction.transactionIndex,
            blockHeight = transaction.minedHeight?.toInt(),
            confirmationsThreshold = confirmationsThreshold,
            timestamp = transaction.timestamp,
            fee = transaction.feePaid?.let { it.convertZatoshiToZec(DECIMAL_COUNT) }
                ?.let { TransactionValue.CoinValue(wallet.token, it) },
            failed = transaction.failed,
            lockInfo = null,
            conflictingHash = null,
            showRawTransaction = false,
            amount = amount,
            to = transaction.toAddress?.let(::listOf),
            from = null,
            changeAddresses = null,
            sentToSelf = false,
            memo = transaction.memo,
            source = wallet.transactionSource,
            replaceable = false,
            transactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
            isIronwoodMigration = isIronwoodMigration
        )
    }

    enum class ZCashAddressType {
        Shielded, Transparent, Unified
    }

    sealed class ZcashError : Exception() {
        object InvalidAddress : ZcashError()
        object SendToSelfNotAllowed : ZcashError()
    }
}

internal fun TransactionSubmitResult.toZcashRawBroadcastResult(): BroadcastRawTransactionResult =
    when (this) {
        is TransactionSubmitResult.Success -> toSubmittedBroadcastResult()
        is TransactionSubmitResult.Failure -> {
            if (description.isZcashAlreadyCommittedToBestChainError()) {
                toAlreadyKnownBroadcastResult()
            } else {
                throw Exception(description ?: "Zcash raw transaction broadcast failed: $code")
            }
        }
        is TransactionSubmitResult.NotAttempted -> throw Exception("Zcash raw transaction broadcast was not attempted")
    }

private fun TransactionSubmitResult.toSubmittedBroadcastResult() =
    BroadcastRawTransactionResult(
        txHash = txIdString().canonicalTransactionHash(),
        status = BroadcastRawTransactionStatus.Submitted,
    )

private fun TransactionSubmitResult.toAlreadyKnownBroadcastResult() =
    BroadcastRawTransactionResult(
        txHash = txIdString().canonicalTransactionHash(),
        status = BroadcastRawTransactionStatus.AlreadyKnown,
    )

internal fun WalletBalance.toBalanceData(decimalCount: Int) = BalanceData(
    available = available.convertZatoshiToZec(decimalCount),
    pending = pending.convertZatoshiToZec(decimalCount)
)

internal fun zcashRestartDelayFor(attempt: Int, baseMs: Long, maxMs: Long): Long =
    (baseMs shl attempt.coerceAtMost(3)).coerceAtMost(maxMs)

object ZcashAddressValidator {
    fun validate(address: String): Boolean {
        return isValidZcashAddress(address)
    }

    fun isTransparentAddress(address: String): Boolean {
        return isValidTransparentAddress(address)
    }

    private fun isValidTransparentAddress(address: String): Boolean {
        val transparentPattern = Pattern.compile("^t[0-9a-zA-Z]{34}$")
        return transparentPattern.matcher(address).matches()
    }

    private fun isValidShieldedAddress(address: String): Boolean {
        val shieldedPattern = Pattern.compile("^z[0-9a-zA-Z]{77}$")
        return shieldedPattern.matcher(address).matches()
    }

    private fun isValidUnifiedAddress(address: String): Boolean {
        val unifiedPattern = Pattern.compile("^u1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{100,220}$")
        return unifiedPattern.matcher(address).matches()
    }

    private fun isValidZcashAddress(address: String): Boolean {
        return isValidTransparentAddress(address) || isValidShieldedAddress(address) || isValidUnifiedAddress(address)
    }
}

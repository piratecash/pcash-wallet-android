package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.core.onPollingStartedSuspend
import cash.p.terminal.core.onPollingStoppedSuspend
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.MoneroAdapter
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import com.m2049r.levin.util.NetCipherHelper
import com.m2049r.xmrwallet.data.DefaultNodes
import com.m2049r.xmrwallet.data.NodeInfo
import com.m2049r.xmrwallet.data.TxData
import com.m2049r.xmrwallet.data.UserNotes
import com.m2049r.xmrwallet.model.PendingTransaction
import com.m2049r.xmrwallet.model.TransactionInfo
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.offline.SignedRawMoneroTransaction
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.m2049r.xmrwallet.service.WalletCorruptedException
import com.m2049r.xmrwallet.util.Helper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.sizeInMb
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class MoneroKitManager(
    private val moneroWalletService: MoneroWalletService,
    private val backgroundManager: BackgroundManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val connectivityManager: ConnectivityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val networkErrorTracker: NetworkErrorTracker,
) {
    // Serializes account-lifecycle mutations (activate / unlink / stop) so the process-global
    // NetCipherHelper observer factory is set/cleared without racing a concurrent teardown.
    private val accountMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val coroutineScope =
        CoroutineScope(dispatcherProvider.io + CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Coroutine error")
        })
    var moneroKitWrapper: MoneroKitWrapper? = null
    private val lifecycleJobs = mutableListOf<Job>()

    private var useCount = AtomicInteger(0)
    var currentAccount: Account? = null
        private set
    private val moneroKitStoppedSubject = PublishSubject.create<Unit>()

    val kitStoppedObservable: Observable<Unit>
        get() = moneroKitStoppedSubject

    suspend fun getMoneroKitWrapper(account: Account): MoneroKitWrapper = accountMutex.withLock {
        if (this.moneroKitWrapper != null && currentAccount != account) {
            stopKit() // also nulls moneroKitWrapper and clears the factory
        }

        if (this.moneroKitWrapper == null) {
            val accountType = account.type
            this.moneroKitWrapper = when {
                accountType is AccountType.MnemonicMonero ||
                        accountType is AccountType.Mnemonic
                    -> createKitInstance(account)

                else -> throw UnsupportedAccountException()
            }
            // Install the passive network observer for THIS account before startKit triggers
            // node-selection pings, so transport errors are attributed to the active account.
            NetCipherHelper.setEventListenerFactory(
                NetworkErrorEventListener.Factory(BlockchainType.Monero, account.id, networkErrorTracker)
            )
            try {
                startKit()
                subscribeToEvents()
            } catch (e: Throwable) {
                // Activation failed or was cancelled before publishing currentAccount: roll back the
                // process-global factory and stop/discard the partial wrapper so no stale state survives.
                // Preserve the original cause: a cleanup-stop failure is suppressed, never rethrown.
                NetCipherHelper.setEventListenerFactory(null)
                val partial = moneroKitWrapper
                moneroKitWrapper = null
                withContext(NonCancellable) {
                    try {
                        partial?.stop()
                    } catch (stopError: Throwable) {
                        e.addSuppressed(stopError)
                    }
                }
                throw e
            }
            useCount.set(0)
            currentAccount = account
        }

        useCount.incrementAndGet()
        requireNotNull(moneroKitWrapper)
    }

    private fun createKitInstance(
        account: Account,
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = moneroWalletService,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = networkErrorTracker,
        )
    }

    suspend fun unlink(account: Account) = accountMutex.withLock {
        if (account == currentAccount) {
            if (useCount.decrementAndGet() < 1) {
                stopKit()
            }
        }
    }

    suspend fun startForPolling() {
        pollingSessionCount.onPollingStartedSuspend {
            resumeOrStartKit()
        }
    }

    suspend fun stopForPolling() {
        pollingSessionCount.onPollingStoppedSuspend(backgroundManager) {
            stopAndSaveKit()
        }
    }

    private suspend fun stopKit() {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()
        currentAccount = null
        // Clear the observer AND invalidate wrapper ownership before the suspending stop, so a
        // concurrent poller restart cannot reuse the stopped wrapper with a null factory, and a
        // stopped account's factory never lingers on the process-global NetCipherHelper.
        NetCipherHelper.setEventListenerFactory(null)
        val wrapper = moneroKitWrapper
        moneroKitWrapper = null
        // Complete the stop under NonCancellable: ownership is already invalidated, so a cancellation
        // here would otherwise orphan a still-open native wallet while the next account starts.
        withContext(NonCancellable) { wrapper?.stop() }
    }

    private suspend fun startKit() {
        moneroKitWrapper?.start()
    }

    private suspend fun stopAndSaveKit() {
        moneroKitWrapper?.stop(saveWallet = true)
    }

    private suspend fun resumeOrStartKit() {
        val wrapper = moneroKitWrapper ?: return
        if (!wrapper.resume()) {
            startKit()
        }
    }

    private fun subscribeToEvents() {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()

        lifecycleJobs += coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    resumeOrStartKit()
                } else if (state == BackgroundManagerState.EnterBackground) {
                    if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero)) {
                        stopAndSaveKit()
                    } else {
                        Timber.tag("TxPoller").d("MoneroKit staying alive")
                    }
                }
            }
        }
        lifecycleJobs += coroutineScope.launch {
            connectivityManager.networkAvailabilityFlow.collect { connected ->
                if (connected && backgroundManager.inForeground) {
                    resumeOrStartKit()
                }
            }
        }
        lifecycleJobs += coroutineScope.launch {
            moneroKitWrapper?.let { w ->
                w.syncState.map { it is AdapterState.Synced }.distinctUntilChanged()
                    .collect { synced ->
                        if (synced) tryOrNull { w.saveSynced() }
                    }
            }
        }
    }
}

class MoneroKitWrapper(
    private val moneroWalletService: MoneroWalletService,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val account: Account,
    private val dispatcherProvider: DispatcherProvider,
    private val networkErrorTracker: NetworkErrorTracker,
) : MoneroWalletService.Observer {
    private val moneroFileDao: MoneroFileDao by inject(MoneroFileDao::class.java)
    private val moneroWalletUseCase: MoneroWalletUseCase by inject(MoneroWalletUseCase::class.java)
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase by inject(
        ValidateMoneroHeightUseCase::class.java
    )
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase by inject(
        RemoveMoneroWalletFilesUseCase::class.java
    )
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase by inject(
        IGetMoneroWalletFilesNameUseCase::class.java
    )
    private val logger = AppLogger("monero-kit").getScoped(account.id)
    @Volatile
    private var lastLoggedConnectionStatus: ConnectionStatus? = null
    private var lastLoggedSyncProgress: Int = -1

    private var isStarted = false
    private var isPaused = false
    private val lifecycleMutex = Mutex()

    @Volatile
    private var storedForSync = false

    private val _syncState = MutableStateFlow<AdapterState>(AdapterState.Syncing())
    val syncState = _syncState.asStateFlow()

    private val _lastBlockInfoFlow = MutableStateFlow<LastBlockInfo?>(null)
    val lastBlockInfoFlow = _lastBlockInfoFlow.asStateFlow()
    private var cachedTotalHeight: Long = 0

    private var walletFileNameForStatus: String? = null

    private val _transactionsStateUpdatedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val transactionsStateUpdatedFlow = _transactionsStateUpdatedFlow.asSharedFlow()


    private suspend fun restoreFromBip39(
        account: Account,
        height: Long
    ) {
        logger.info("restoreFromBip39: height=$height")
        val accountType = account.type as? AccountType.Mnemonic
            ?: throw UnsupportedAccountException()
        val restoredAccount = moneroWalletUseCase.restoreFromBip39(
            accountType.words,
            accountType.passphrase,
            height
        ) ?: throw IllegalStateException("Failed to restore account from 12 words")
        moneroFileDao.insert(
            MoneroFileRecord(
                fileName = SecretString(restoredAccount.walletInnerName),
                password = SecretString(restoredAccount.password),
                accountId = account.id
            )
        )
    }

    suspend fun start(fixIfCorruptedFile: Boolean = true) = lifecycleMutex.withLock {
        startInternal(fixIfCorruptedFile)
    }

    private suspend fun startInternal(fixIfCorruptedFile: Boolean = true) =
        withContext(Dispatchers.IO) {
            if (!isStarted) {
                logger.info("start: requested, fixIfCorruptedFile=$fixIfCorruptedFile, isStarted=$isStarted")
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                storedForSync = false
                _syncState.value = AdapterState.Connecting
                try {
                    val walletFileName: String
                    val walletPassword: String
                    walletFileNameForStatus = null
                    when (val accountType = account.type) {
                        is AccountType.MnemonicMonero -> {
                            logger.info("start: using AccountType.MnemonicMonero")
                            walletFileName = accountType.walletInnerName
                            walletPassword = accountType.password

                            if (!Helper.getWalletFile(App.instance, walletFileName).exists()) {
                                Timber.d("Restoring Monero wallet from mnemonic...")
                                // restore wallet file if it does not exist
                                logger.info("start: wallet file does not exist, restoring from mnemonic")
                                moneroWalletUseCase.restore(
                                    words = accountType.words,
                                    height = accountType.height,
                                    crazyPassExisting = walletPassword,
                                    walletInnerNameExisting = walletFileName
                                )
                            }
                        }

                        is AccountType.Mnemonic -> {
                            logger.info("start: using AccountType.Mnemonic")
                            // Enable first time
                            if (moneroFileDao.getAssociatedRecord(account.id) == null) {
                                logger.info("start: no associated wallet files, restoring from mnemonic")
                                val restoreSettings =
                                    restoreSettingsManager.settings(account, BlockchainType.Monero)
                                val height = restoreSettings.birthdayHeight
                                    ?: validateMoneroHeightUseCase.getTodayHeight()
                                if (height == -1L) {
                                    throw IllegalStateException("Monero restore height can't be -1")
                                }
                                restoreFromBip39(
                                    account = account,
                                    height = height
                                )
                            }

                            requireNotNull(
                                moneroFileDao.getAssociatedRecord(accountId = account.id),
                                { "Account does not have a valid Monero file association" }
                            ).run {
                                walletFileName = this.fileName.value
                                walletPassword = this.password.value
                            }
                        }

                        else -> throw UnsupportedAccountException()
                    }
                    walletFileNameForStatus = walletFileName

                    val selectedNode = MoneroConfig.autoSelectNode()
                    if (selectedNode != null) {
                        logger.info("start: auto-selected node=$selectedNode")
                        WalletManager.getInstance()
                            .setDaemon(selectedNode)
                    } else {
                        logger.info("start: autoSelectNode returned null, set first default node")
                        WalletManager.getInstance()
                            .setDaemon(NodeInfo.fromString(DefaultNodes.entries.first().uri))
                    }

                    /*val walletFolder: File = Helper.getWalletRoot(App.instance)
                    val walletKeyFile = File(walletFolder, "$walletFileName.keys")
                    fixCorruptedWalletFile(walletKeyFile.absolutePath, walletPassword)*/

                    moneroWalletService.setObserver(this@MoneroKitWrapper)
                    logger.info("start: invoking startService for walletFileName=$walletFileName")
                    startService(walletFileName, walletPassword, fixIfCorruptedFile)
                    isStarted = true
                    logger.info(
                        "start: completed startService, connection=${moneroWalletService.connectionStatus}, walletStatus=${moneroWalletService.wallet?.status}"
                    )
                    fixWalletHeight()
                } catch (e: Exception) {
                    _syncState.value = AdapterState.NotSynced(e)
                    logger.warning("start: failed with exception", e)
                    Timber.e(e, "Failed to start Monero wallet")
                }
            }
        }

    private suspend fun startService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean
    ) {
        logger.info("startService: start walletFileName=$walletFileName fixIfCorruptedFile=$fixIfCorruptedFile")
        try {
            val walletStatus = moneroWalletService.start(walletFileName, walletPassword)
            logger.info(
                "startService: initial status=${walletStatus?.toString()} isOk=${walletStatus?.isOk} connection=${walletStatus?.connectionStatus}"
            )
            // Route EVERY start() result through the self-gated helper (incl. a successful Connected
            // start, which arms lastLoggedConnectionStatus so the first onRefreshed is not a "new" transition).
            recordNativeConnectionError(walletStatus?.connectionStatus, walletStatus?.errorString)
            if (walletStatus?.isOk != true) {
                logger.info("startService: wallet status not ok, scheduling retry")
                Timber.d("Monero wallet start error: $walletStatus, restarting")
                if (walletStatus?.connectionStatus == ConnectionStatus.ConnectionStatus_Disconnected) {
                    logger.info("startService: detected disconnected status, aborting start")
                    throw Exception("No internet connection")
                } else if (walletStatus == null) {
                    // Possible corrupted wallet file
                    getBirthdayHeight(account)?.let {
                        resetWalletAndRestart(it)
                    }
                } else {
                    retryStart(walletFileName, walletPassword)
                }
            }
        } catch (e: WalletCorruptedException) {
            logger.warning("startService: WalletCorruptedException received", e)
            try {
                if (fixIfCorruptedFile) {
                    if (e.message?.contains("std::bad_alloc") == true) { // too big cache file
                        val cacheFileSize = tryOrNull { getCacheFile().sizeInMb() } ?: ""
                        val deleted = tryOrNull { getCacheFile()?.delete() } ?: false
                        Timber.d("MoneroKitManager: detected bad_alloc error(size: $cacheFileSize), deleted cache file: $deleted")
                        logger.info("startService: detected bad_alloc error(size: $cacheFileSize), deleting cache file, deleted=$deleted")
                        startService(walletFileName, walletPassword, false)
                        return
                    }
                    Timber.e(
                        e,
                        "WalletCorruptedException, trying to fix wallet, cache size: ${tryOrNull { getCacheFile().sizeInMb() } ?: "unknown"}"
                    )
                    logger.info("startService: attempting wallet fix after corruption")
                    getBirthdayHeight(account)?.let {
                        resetWalletAndRestart(it)
                    }
                } else {
                    Timber.e(e, "WalletCorruptedException, fix disabled")
                    logger.info("startService: wallet fix disabled, corruption remains")
                }
            } catch (ex: Exception) {
                logger.warning("startService: failed while handling WalletCorruptedException", ex)
                Timber.e(ex, "Failed to fix corrupted wallet")
            }
        } catch (e: Exception) {
            logger.warning("startService: unexpected exception", e)
            throw e
        }
    }

    private suspend fun retryStart(walletFileName: String, walletPassword: String) {
        delay(3_000)
        val retryStatus = moneroWalletService.start(walletFileName, walletPassword)
        logger.info(
            "startService: retry status=${retryStatus?.toString()} isOk=${retryStatus?.isOk} connection=${retryStatus?.connectionStatus}"
        )
        recordNativeConnectionError(retryStatus?.connectionStatus, retryStatus?.errorString)
    }

    private fun getBirthdayHeight(account: Account): Long? {
        var birthdayHeight = restoreSettingsManager.settings(
            account,
            BlockchainType.Monero
        ).birthdayHeight
        if ((birthdayHeight ?: 0L) <= 0L) {
            birthdayHeight = (account.type as? AccountType.MnemonicMonero)?.height
        }
        return birthdayHeight
    }

    /**
     * @return true if wallet need to be fixed
     */
    private suspend fun fixCorruptedWalletFile(
        walletKeysFileName: String,
        walletPassword: String
    ) {
        logger.info("fixCorruptedWalletFile: check walletKeysFileName=$walletKeysFileName")
        if ((account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12) return

        if (WalletManager.getInstance()
                .verifyWalletPassword(walletKeysFileName, walletPassword, false)
        ) return

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        Timber.d("Fixing corrupted wallet file with height: ${restoreSettings.birthdayHeight}")
        logger.info("fixCorruptedWalletFile: fixing with restoreHeight=${restoreSettings.birthdayHeight}")
        restoreSettings.birthdayHeight?.let {
            resetWalletAndRestart(it)
        }
    }

    private suspend fun fixWalletHeight() {
        if (moneroWalletService.wallet?.restoreHeight != -1L ||
            (account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12
        ) return

        logger.info("fixWalletHeight: restoreHeight missing, resetting to validated height")
        // Use day of publishing this changes on google play as height
        // to fix possible first day of using this feature by users
        resetWalletAndRestart(validateMoneroHeightUseCase("2025-08-13"))
    }

    private suspend fun resetWalletAndRestart(birthdayHeight: Long) {
        logger.info("resetWalletAndRestart: requested with birthdayHeight=$birthdayHeight")
        stopInternal(false)
        getMoneroWalletFilesNameUseCase(account)?.also {
            val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
            val heightNeedToUpdate = restoreSettings.birthdayHeight != birthdayHeight
            logger.info("resetWalletAndRestart: delete walletFile=$it heightNeedsUpdate=$heightNeedToUpdate")
            if (heightNeedToUpdate) {
                restoreSettings.birthdayHeight = birthdayHeight
            }

            removeMoneroWalletFilesUseCase(it)
            moneroFileDao.deleteAssociatedRecord(account.id)

            if (heightNeedToUpdate) {
                restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)
            }
        }
        startInternal(fixIfCorruptedFile = false)
        logger.info("resetWalletAndRestart: restart complete")
    }

    suspend fun stop(saveWallet: Boolean = true) = lifecycleMutex.withLock {
        stopInternal(saveWallet)
    }

    private suspend fun stopInternal(saveWallet: Boolean = true) = withContext(Dispatchers.IO) {
        if (isStarted) {
            logger.info("stop: stopping service saveWallet=$saveWallet")
            isStarted = false
            isPaused = false
            moneroWalletService.stop(saveWallet)
            lastLoggedSyncProgress = -1
            lastLoggedConnectionStatus = null
            logger.info("stop: service stopped")
        } else {
            logger.info("stop: skip, service already stopped")
        }
    }

    suspend fun pause() = lifecycleMutex.withLock {
        if (isStarted && !isPaused) {
            logger.info("pause: pausing wallet refresh")
            moneroWalletService.pause()
            isPaused = true
            logger.info("pause: done")
        } else {
            logger.info("pause: skip, isStarted=$isStarted isPaused=$isPaused")
        }
    }

    suspend fun resume(): Boolean = lifecycleMutex.withLock {
        resumeInternal()
    }

    private fun resumeInternal(): Boolean {
        if (isStarted && isPaused) {
            logger.info("resume: resuming wallet refresh")
            val resumed = moneroWalletService.resume(this)
            if (resumed) {
                isPaused = false
                logger.info("resume: done")
            } else {
                logger.info("resume: service resume returned false")
            }
            return resumed
        } else {
            logger.info("resume: skip, isStarted=$isStarted isPaused=$isPaused")
            return false
        }
    }

    /**
     * Persists the wallet cache via a SIGSEGV-guarded native store, once per session,
     * the first time sync reaches [AdapterState.Synced]. Runs OFF the refresh thread
     * (dispatcherProvider.io) and is serialized with stop()/pause()/resume()/send() via
     * lifecycleMutex, so it never races a concurrent native call on the wallet.
     */
    suspend fun saveSynced(): Boolean = lifecycleMutex.withLock {
        withContext(dispatcherProvider.io) {
            if (!isStarted || isPaused || storedForSync) return@withContext storedForSync
            var status = -1
            try {
                moneroWalletService.pause()
                delay(200)
                var attempt = 0
                while (attempt < 2) {
                    status = moneroWalletService.wallet?.storeSafe() ?: -1
                    if (status != 1) break
                    attempt++
                    delay(200)
                }
                when (status) {
                    0 -> {
                        storedForSync = true
                        logger.info("saveSynced: stored at height=${moneroWalletService.wallet?.blockChainHeight}")
                    }

                    2 -> logger.warning(
                        "saveSynced: SIGSEGV, wallet abandoned",
                        IllegalStateException("storeSafe faulted with SIGSEGV")
                    )

                    else -> logger.info("saveSynced: storeSafe status=$status")
                }
            } finally {
                if (status == 2) {
                    // storeSafe zeroed the native handle; clear ownership by identity
                    // (no native calls on the dead wallet), then reopen fresh with
                    // corruption recovery enabled (post-fault file state uncertain).
                    moneroWalletService.abandonFaultedWallet()
                    isStarted = false
                    startInternal(fixIfCorruptedFile = true)
                } else {
                    moneroWalletService.resume(this@MoneroKitWrapper)
                }
            }
            storedForSync
        }
    }

    suspend fun refresh() = lifecycleMutex.withLock {
        if (_syncState.value is AdapterState.Syncing) {
            logger.info("refresh: skip, already syncing")
            Timber.d("MoneroKitWrapper: Already syncing, skipping refresh")
            return@withLock
        }
        try {
            if (isStarted) {
                refreshStartedWallet()
            } else {
                logger.info("refresh: starting wallet")
                startInternal()
            }
        } catch (e: Exception) {
            logger.warning("refresh: failed to refresh wallet", e)
            Timber.e(e, "Failed to refresh Monero wallet")
        }
    }

    private suspend fun refreshStartedWallet() {
        if (isPaused && !resumeInternal()) {
            logger.info("refresh: restarting wallet after failed resume")
            restartInternal()
            return
        }

        if (requestWalletRefresh()) {
            logger.info("refresh: requesting wallet refresh")
        } else {
            logger.info("refresh: restarting wallet")
            restartInternal()
        }
    }

    private suspend fun restartInternal() {
        stopInternal(saveWallet = false)
        startInternal()
    }

    private fun requestWalletRefresh(): Boolean {
        val wallet = moneroWalletService.wallet ?: return false
        wallet.refreshAsync()
        return true
    }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-send.
    suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): String = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            val txData = buildTxData(amount, address, memo, wallet)
            moneroWalletService.prepareTransaction(txData)
            moneroWalletService.sendTransaction(memo)
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun createSignedRawTransaction(
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): SignedRawMoneroTransaction = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            moneroWalletService.createSignedRawTransaction(buildTxData(amount, address, memo, wallet))
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun submitSignedRawTransaction(raw: ByteArray): RawMoneroBroadcastResult =
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                moneroWalletService.submitSignedRawTransaction(raw)
            }
        }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-estimate.
    suspend fun estimateFee(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): Long = lifecycleMutex.withLock {
        val wallet = moneroWalletService.wallet
            ?: throw IllegalStateException("Monero wallet not initialized")
        val txData = buildTxData(amount, address, memo, wallet)
        val fee = wallet.estimateTransactionFee(txData)
        if (fee < 0) {
            throw IllegalStateException("Failed to estimate fee: wallet not synced with daemon")
        }
        fee
    }

    private fun buildTxData(
        amount: BigDecimal,
        address: String,
        memo: String?,
        wallet: Wallet
    ) = TxData().apply {
        this.destination = address
        this.amount = amount.movePointRight(MoneroAdapter.decimal).toLong()
        this.mixin = wallet.defaultMixin
        this.priority = PendingTransaction.Priority.Priority_Default
        memo?.let {
            this.userNotes = UserNotes(it)
        }
    }

    fun statusInfo(): Map<String, Any> {
        logger.info(
            "statusInfo: connection=${moneroWalletService.connectionStatus} wallet=${moneroWalletService.wallet?.status} isStarted=$isStarted restoreHeight=${moneroWalletService.wallet?.restoreHeight}"
        )
        val base = mapOf<String, Any>(
            "connectionStatus" to moneroWalletService.connectionStatus,
            "walletStatus" to moneroWalletService.wallet?.status?.toString().orEmpty(),
            "isStarted" to isStarted,
            "Birthday Height" to (getBirthdayHeight(account) ?: "Not set"),
            "Cache file size" to (tryOrNull { getCacheFile().sizeInMb() } ?: "")
        )
        return networkErrorTracker.appendNetworkErrors(base, BlockchainType.Monero, account.id)
    }

    /**
     * Records a native (non-OkHttp) connection error into the shared tracker. Self-gated on
     * [lastLoggedConnectionStatus] so repeated same-status callbacks record once; ignores null and
     * Connected. Passive: never throws into the caller.
     */
    private fun recordNativeConnectionError(status: ConnectionStatus?, errorString: String?) {
        val s = status ?: return
        if (s == lastLoggedConnectionStatus) return
        lastLoggedConnectionStatus = s
        if (s == ConnectionStatus.ConnectionStatus_Connected) return
        tryOrNull {
            val addr = tryOrNull { WalletManager.getInstance().getDaemonAddress() }.orEmpty()
            networkErrorTracker.record(
                BlockchainType.Monero,
                account.id,
                NetworkErrorInfo(
                    source = "Monero",
                    method = s.name,
                    url = addr,
                    host = addr,
                    resolvedIps = emptyList(),
                    throwable = IllegalStateException(
                        errorString?.takeIf { it.isNotBlank() } ?: "Not connected"
                    ),
                )
            )
        }
    }

    private fun getCacheFile(): File? {
        return walletFileNameForStatus?.let { Helper.getWalletFile(App.instance, it) }
    }

    // Add methods for balance, transactions, etc.
    fun getBalance(): Long {
        return try {
            Timber.d("getBalance: ${moneroWalletService.wallet?.balance}")
            moneroWalletService.wallet?.balance ?: 0L
        } catch (e: Exception) {
            logger.warning("getBalance: failed to fetch balance", e)
            Timber.d("getBalance: Failed to get balance")
            0L
        }
    }

    fun getUnlockedBalance(): Long {
        return try {
            moneroWalletService.wallet?.unlockedBalance ?: 0L
        } catch (e: Exception) {
            logger.warning("getUnlockedBalance: failed to fetch unlocked balance", e)
            0L
        }
    }

    fun getAddress(): String {
        return try {
            requireNotNull(moneroWalletService.wallet).address
        } catch (e: Exception) {
            logger.warning("getAddress: failed to fetch address", e)
            Timber.d("getAddress: Failed to get address $e")
            ""
        }
    }

    suspend fun getSubaddresses(): List<MoneroSubaddressInfo> = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet ?: return@withContext emptyList()
            val count = wallet.numSubaddresses
            (0 until count).map { index ->
                val subaddress = wallet.getSubaddressObject(index)
                MoneroSubaddressInfo(
                    index = subaddress.addressIndex,
                    address = subaddress.address,
                    receivedAmount = subaddress.amount
                )
            }
        }
    }

    suspend fun createNewSubaddress(): String = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            wallet.newSubaddress
        }
    }

    fun getTransactions(): List<TransactionInfo> {
        if (!isStarted) return emptyList()
        return try {
            var transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
            if (transactions.isEmpty()) {
                moneroWalletService.wallet?.let {
                    moneroWalletService.wallet?.history?.refreshWithNotes(it)
                    transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
                }
            }
            transactions
        } catch (e: Exception) {
            logger.warning("getTransactions: failed to fetch transactions", e)
            Timber.d("getTransactions: Failed to get transactions")
            emptyList()
        }
    }

    override fun onRefreshed(
        wallet: Wallet?,
        full: Boolean
    ): Boolean {
        if (!isStarted) return false

        val connectionStatus = tryOrNull { moneroWalletService.connectionStatus }
            ?: ConnectionStatus.ConnectionStatus_Disconnected
        // Native status can additionally report WrongVersion, which the service status never carries.
        val observed = tryOrNull { wallet?.connectionStatus } ?: connectionStatus

        if (observed != lastLoggedConnectionStatus) {
            logger.info(
                "onRefreshed: connection=$observed full=$full " +
                    "isSynchronized=${wallet?.isSynchronized} " +
                    "daemonHeight=${moneroWalletService.daemonHeight} " +
                    "walletHeight=${wallet?.blockChainHeight}"
            )
        }
        recordNativeConnectionError(observed, tryOrNull { wallet?.status?.errorString })

        if (connectionStatus == ConnectionStatus.ConnectionStatus_Connected) {
            _lastBlockInfoFlow.value = if (moneroWalletService.daemonHeight != 0L) {
                LastBlockInfo(moneroWalletService.daemonHeight.toInt())
            } else {
                null
            }
        }

        if (connectionStatus == ConnectionStatus.ConnectionStatus_Connected) {
            _transactionsStateUpdatedFlow.tryEmit(Unit)
        }

        _syncState.value =
            if (connectionStatus != ConnectionStatus.ConnectionStatus_Connected) {
                Timber.d("MoneroKitWrapper: Not connected")
                logger.info("onRefreshed: connection lost, setting state=NotSynced")
                lastLoggedSyncProgress = -1
                AdapterState.NotSynced(IllegalStateException("Not connected"))
            } else if (moneroWalletService.wallet?.isSynchronized == true) {
                Timber.d("MoneroKitWrapper: Synced")
                logger.info(
                    "onRefreshed: wallet synchronized at height=${moneroWalletService.wallet?.blockChainHeight}"
                )
                lastLoggedSyncProgress = 100
                AdapterState.Synced
            } else {
                Timber.d("MoneroKitWrapper: Sync in progress")
                val currentHeight = tryOrNull { moneroWalletService.wallet?.blockChainHeight } ?: 0
                val totalHeight = tryOrNull { WalletManager.getInstance().blockchainHeight } ?: 0L
                if (totalHeight > 0) {
                    cachedTotalHeight = totalHeight
                }
                val heightToUse = if (totalHeight > 0) totalHeight else cachedTotalHeight
                Timber.d("currentHeight = $currentHeight, totalHeight = $totalHeight")

                val progressPercent = if (heightToUse > 0) {
                    ((currentHeight.toDouble() / heightToUse) * 100).coerceIn(0.0, 100.0).toInt()
                } else {
                    0
                }

                val blocksRemained = if (heightToUse > 0 && currentHeight < heightToUse) {
                    heightToUse - currentHeight
                } else {
                    null
                }

                if (progressPercent == 0 && lastLoggedSyncProgress != 0) {
                    logger.info("onRefreshed: sync progress started (0%)")
                }
                if (progressPercent >= 100 && lastLoggedSyncProgress < 100) {
                    logger.info("onRefreshed: sync progress reached 100%")
                }
                if (progressPercent - lastLoggedSyncProgress >= 5 && progressPercent in 1..99) {
                    logger.info("onRefreshed: sync progress=${progressPercent}% blocksRemained=$blocksRemained")
                }
                lastLoggedSyncProgress = progressPercent

                AdapterState.Syncing(
                    progress = progressPercent.toDouble(),
                    blocksRemained = blocksRemained
                )
            }
        Timber
            .d("onRefreshed, isSynchronized = ${wallet?.isSynchronized}, connectionStatus = ${wallet?.connectionStatus}, full = $full, restoreHeight = ${moneroWalletService.wallet?.restoreHeight?.toString()}")
        return true
    }

    override fun onProgress(text: String?) {
        if (!text.isNullOrBlank()) {
            logger.info("onProgress(text): $text")
        }
        Timber.d("onProgress: $text")
    }

    override fun onProgress(n: Int) {
        if (n % 10 == 0) {
            logger.info("onProgress(value): $n")
        }
        Timber.d("onProgress: $n")
    }

    override fun onWalletStarted(walletStatus: Wallet.Status?) {
        logger.info("onWalletStarted: status=${walletStatus?.toString()} isSynchronized=${moneroWalletService.wallet?.isSynchronized}")
        Timber.d("onWalletStarted: $walletStatus")
        if (moneroWalletService.wallet?.isSynchronized == true) {
            println("MoneroKitWrapper: Synced")
            _syncState.value = AdapterState.Synced
        }
    }

    override fun onWalletOpen(device: Wallet.Device?) {
        Timber.d("onWalletOpen: $device")
    }
}

package cash.p.terminal.core.managers

import android.util.Log
import androidx.room.concurrent.AtomicInt
import cash.p.terminal.core.App
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.MoneroAdapter
import cash.p.terminal.core.storage.MoneroFileDao
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
import com.m2049r.xmrwallet.data.TxData
import com.m2049r.xmrwallet.data.UserNotes
import com.m2049r.xmrwallet.model.PendingTransaction
import com.m2049r.xmrwallet.model.TransactionInfo
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.m2049r.xmrwallet.service.WalletCorruptedException
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.SafeSuspendedCall
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.math.BigDecimal

class MoneroKitManager(
    private val moneroWalletService: MoneroWalletService,
    private val backgroundManager: BackgroundManager,
    private val restoreSettingsManager: RestoreSettingsManager
) {
    private val connectivityManager = App.connectivityManager
    private val coroutineScope =
        CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.d("MoneroKitManager", "Coroutine error", throwable)
        })
    var moneroKitWrapper: MoneroKitWrapper? = null

    private var useCount = AtomicInt(0)
    var currentAccount: Account? = null
        private set
    private val moneroKitStoppedSubject = PublishSubject.create<Unit>()

    val kitStoppedObservable: Observable<Unit>
        get() = moneroKitStoppedSubject

    suspend fun getMoneroKitWrapper(account: Account): MoneroKitWrapper {
        if (this.moneroKitWrapper != null && currentAccount != account) {
            stopKit()
            moneroKitWrapper = null
        }

        if (this.moneroKitWrapper == null) {
            val accountType = account.type
            this.moneroKitWrapper = when {
                accountType is AccountType.MnemonicMonero ||
                        accountType is AccountType.Mnemonic
                    -> createKitInstance(account)

                else -> throw UnsupportedAccountException()
            }
            startKit()
            subscribeToEvents()
            useCount.set(0)
            currentAccount = account
        }

        useCount.incrementAndGet()
        return this.moneroKitWrapper!!
    }

    private fun createKitInstance(
        account: Account,
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = moneroWalletService,
            restoreSettingsManager = restoreSettingsManager,
            account = account
        )
    }

    suspend fun unlinkAll() {
        currentAccount = null
        useCount.set(0)
        stopKit()
    }

    suspend fun unlink(account: Account) {
        if (account == currentAccount) {
            if (useCount.decrementAndGet() < 1) {
                stopKit()
            }
        }
    }

    private suspend fun stopKit() {
        currentAccount = null
        moneroKitWrapper?.stop()
    }

    private suspend fun startKit() {
        moneroKitWrapper?.start()
    }

    private fun subscribeToEvents() {
        coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    startKit()
                } else if (state == BackgroundManagerState.EnterBackground) {
                    stopKit()
                }
            }
        }
        coroutineScope.launch {
            connectivityManager.networkAvailabilityFlow.collect { connected ->
                println("Connection status: $connected")
                if (connected) {
                    startKit()
                }
            }
        }
    }
}

class MoneroKitWrapper(
    private val moneroWalletService: MoneroWalletService,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val account: Account
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

    private var isStarted = false

    private val _syncState = MutableStateFlow<AdapterState>(AdapterState.Syncing())
    val syncState = _syncState.asStateFlow()

    private val _lastBlockInfoFlow = MutableStateFlow<LastBlockInfo?>(null)
    val lastBlockInfoFlow = _lastBlockInfoFlow.asStateFlow()
    private var cachedTotalHeight: Long = 0

    private val _transactionsStateUpdatedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val transactionsStateUpdatedFlow = _transactionsStateUpdatedFlow.asSharedFlow()

    private suspend fun restoreFromBip39(
        account: Account,
        height: Long
    ) {
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

    suspend fun start(fixIfCorruptedFile: Boolean = true) = withContext(Dispatchers.IO) {
        if (!isStarted) {
            try {
                val walletFileName: String
                val walletPassword: String
                val accountType = account.type
                when (accountType) {
                    is AccountType.MnemonicMonero -> {
                        walletFileName = accountType.walletInnerName
                        walletPassword = accountType.password
                    }

                    is AccountType.Mnemonic -> {
                        // Enable first time
                        if (moneroFileDao.getAssociatedRecord(account.id) == null) {
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

                MoneroConfig.autoSelectNode()?.let {
                    WalletManager.getInstance()
                        .setDaemon(it)
                } ?: throw IllegalStateException("No nodes available")

                /*val walletFolder: File = Helper.getWalletRoot(App.instance)
                val walletKeyFile = File(walletFolder, "$walletFileName.keys")
                fixCorruptedWalletFile(walletKeyFile.absolutePath, walletPassword)*/

                moneroWalletService.setObserver(this@MoneroKitWrapper)
                startService(walletFileName, walletPassword, fixIfCorruptedFile)
                isStarted = true

                fixWalletHeight()
            } catch (e: Exception) {
                _syncState.value = AdapterState.NotSynced(e)
                Timber.e(e, "Failed to start Monero wallet")
            }
        }
    }

    private suspend fun startService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean
    ) {
        try {
            val walletStatus = moneroWalletService.start(walletFileName, walletPassword)
            if (walletStatus?.isOk != true) {
                Timber.d("Monero wallet start error: $walletStatus, restarting")
                delay(3_000)
                moneroWalletService.start(walletFileName, walletPassword)
            }
        } catch (e: WalletCorruptedException) {
            if (fixIfCorruptedFile) {
                Timber.e(e, "WalletCorruptedException, trying to fix wallet")
                restoreSettingsManager.settings(
                    account,
                    BlockchainType.Monero
                ).birthdayHeight?.let {
                    resetWalletAndRestart(it)
                }
            } else {
                Timber.e(e, "WalletCorruptedException, fix disabled")
            }
        }
    }

    /**
     * @return true if wallet need to be fixed
     */
    private suspend fun fixCorruptedWalletFile(
        walletKeysFileName: String,
        walletPassword: String
    ) {
        if ((account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12) return

        if (WalletManager.getInstance()
                .verifyWalletPassword(walletKeysFileName, walletPassword, false)
        ) return

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        Timber.d("Fixing corrupted wallet file with height: ${restoreSettings.birthdayHeight}")
        restoreSettings.birthdayHeight?.let {
            resetWalletAndRestart(it)
        }
    }

    private suspend fun fixWalletHeight() {
        if (moneroWalletService.wallet?.restoreHeight != -1L ||
            (account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12
        ) return

        // Use day of publishing this changes on google play as height
        // to fix possible first day of using this feature by users
        resetWalletAndRestart(validateMoneroHeightUseCase("2025-08-13"))
    }

    private suspend fun resetWalletAndRestart(birthdayHeight: Long) {
        stop(false)
        getMoneroWalletFilesNameUseCase(account)?.also {
            val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
            val heightNeedToUpdate = restoreSettings.birthdayHeight != birthdayHeight
            if (heightNeedToUpdate) {
                restoreSettings.birthdayHeight = birthdayHeight
            }

            removeMoneroWalletFilesUseCase(it)
            moneroFileDao.deleteAssociatedRecord(account.id)

            if (heightNeedToUpdate) {
                restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)
            }
        }
        start(fixIfCorruptedFile = false)
    }

    suspend fun stop(saveWallet: Boolean = true) = SafeSuspendedCall.executeSuspendable {
        withContext(Dispatchers.IO) {
            if (isStarted) {
                isStarted = false
                moneroWalletService.stop(saveWallet)
            }
        }
    }

    suspend fun refresh() {
        if (isStarted) {
            try {
                stop()
                start()
            } catch (e: Exception) {
                Log.e("MoneroKitWrapper", "Failed to refresh Monero wallet", e)
            }
        }
    }

    fun send(
        amount: BigDecimal,
        address: String,
        memo: String?
    ) {
        val txData = buildTxData(amount, address, memo)

        moneroWalletService.prepareTransaction("send", txData)
        moneroWalletService.sendTransaction(memo)
    }

    fun estimateFee(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): Long {
        val txData = buildTxData(amount, address, memo)
        return moneroWalletService.wallet!!.estimateTransactionFee(txData)
    }

    private fun buildTxData(
        amount: BigDecimal,
        address: String,
        memo: String?
    ) = TxData().apply {
        this.destination = address
        this.amount = amount.movePointRight(MoneroAdapter.decimal).toLong()
        this.mixin = moneroWalletService.wallet!!.defaultMixin
        this.priority = PendingTransaction.Priority.Priority_Default
        memo?.let {
            this.userNotes = UserNotes(it)
        }
    }

    fun statusInfo(): Map<String, Any> {
        return mapOf(
            "connectionStatus" to moneroWalletService.connectionStatus,
            "walletStatus" to moneroWalletService.wallet?.status?.toString().orEmpty(),
            "isStarted" to isStarted,
            "Birthday Height" to (moneroWalletService.wallet?.restoreHeight?.toString()
                ?: "Not set"),
        )
    }

    // Add methods for balance, transactions, etc.
    fun getBalance(): Long {
        return try {
            Timber.d("getBalance: ${moneroWalletService.wallet?.balance}")
            moneroWalletService.wallet?.balance ?: 0L
        } catch (e: Exception) {
            Timber.d("getBalance: Failed to get balance")
            0L
        }
    }

    fun getAddress(): String {
        return try {
            moneroWalletService.wallet!!.address
        } catch (e: Exception) {
            Log.e("MoneroKitWrapper", "Failed to get address", e)
            ""
        }
    }

    fun getTransactions(): List<TransactionInfo> {
        return try {
            moneroWalletService.wallet?.history?.all ?: emptyList()
        } catch (e: Exception) {
            Timber.d("getTransactions: Failed to get transactions")
            emptyList()
        }
    }

    override fun onRefreshed(
        wallet: Wallet?,
        full: Boolean
    ): Boolean {
        if (moneroWalletService.connectionStatus == ConnectionStatus.ConnectionStatus_Connected) {
            _lastBlockInfoFlow.value = if (moneroWalletService.daemonHeight != 0L) {
                LastBlockInfo(moneroWalletService.daemonHeight.toInt())
            } else {
                null
            }
        }

        if (moneroWalletService.connectionStatus == ConnectionStatus.ConnectionStatus_Connected) {
            _transactionsStateUpdatedFlow.tryEmit(Unit)
        }

        _syncState.value =
            if (moneroWalletService.connectionStatus != ConnectionStatus.ConnectionStatus_Connected) {
                Timber.d("MoneroKitWrapper: Not connected")
                AdapterState.NotSynced(IllegalStateException("Not connected"))
            } else if (moneroWalletService.wallet?.isSynchronized == true) {
                Timber.d("MoneroKitWrapper: Synced")
                AdapterState.Synced
            } else {
                Timber.d("MoneroKitWrapper: Sync in progress")
                val progressPercent = runCatching {
                    val currentHeight = moneroWalletService.wallet?.blockChainHeight ?: 0
                    val totalHeight = WalletManager.getInstance().blockchainHeight
                    if (totalHeight > 0) {
                        cachedTotalHeight = totalHeight
                    }
                    val heightToUse = if (totalHeight > 0) totalHeight else cachedTotalHeight
                    Timber.d("currentHeight = $currentHeight, totalHeight = $totalHeight")

                    if (heightToUse > 0) {
                        ((currentHeight.toDouble() / heightToUse) * 100).coerceIn(0.0, 100.0)
                            .toInt()
                    } else {
                        0
                    }
                }.getOrElse { 0 }

                AdapterState.Syncing(progressPercent.toInt())
            }
        Timber
            .d("onRefreshed, isSynchronized = ${wallet?.isSynchronized}, connectionStatus = ${wallet?.connectionStatus}, full = $full, restoreHeight = ${moneroWalletService.wallet?.restoreHeight?.toString()}")
        return true
    }

    override fun onProgress(text: String?) {
        Timber.d("onProgress: $text")
    }

    override fun onProgress(n: Int) {
        Timber.d("onProgress: $n")
    }

    override fun onWalletStored(success: Boolean) {
        Timber.d("onWalletStored: $success")
    }

    override fun onTransactionCreated(
        tag: String?,
        pendingTransaction: PendingTransaction?
    ) {
        Timber.d("onTransactionCreated: $tag, $pendingTransaction")
    }

    override fun onTransactionSent(txid: String?) {
        Timber.d("onTransactionSent: $txid")
    }

    override fun onSendTransactionFailed(error: String?) {
        Timber.d("onSendTransactionFailed: $error")
    }

    override fun onWalletStarted(walletStatus: Wallet.Status?) {
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

package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.tangem.signer.HardwareWalletStellarSigner
import cash.p.terminal.trezor.signer.TrezorStellarSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.Network
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.stellarkit.StellarWallet
import io.horizontalsystems.stellarkit.SyncState
import io.horizontalsystems.stellarkit.room.StellarAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.stellar.sdk.KeyPair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class StellarKitManager(
    private val backgroundManager: BackgroundManager,
    private val hardwarePublicKeyStorage: HardwarePublicKeyStorage,
    private val trezorClient: ITrezorClient,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
) {
    private val lifecycleMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private val _kitStartedFlow = MutableStateFlow(false)
    val kitStartedFlow: StateFlow<Boolean> = _kitStartedFlow

    var stellarKitWrapper: StellarKitWrapper? = null
        private set(value) {
            field = value

            _kitStartedFlow.update { value != null }
        }

    private var useCount = 0
    var currentAccount: Account? = null
        private set

    val statusInfo: Map<String, Any>?
        get() = networkErrorTracker.mergedStatusInfo(
            stellarKitWrapper?.stellarKit?.statusInfo(),
            BlockchainType.Stellar,
            currentAccount?.id,
        )

    suspend fun getStellarKitWrapper(account: Account): StellarKitWrapper =
        lifecycleMutex.withLock {
            if (this.stellarKitWrapper != null && currentAccount != account) {
                stop()
            }

            if (this.stellarKitWrapper == null) {
                val accountType = account.type
                val wrapper = when (accountType) {
                    is AccountType.Mnemonic,
                    is AccountType.StellarAddress,
                    is AccountType.HardwareCard,
                    is AccountType.StellarSecretKey -> {
                        createKitInstance(accountType, account)
                    }

                    is AccountType.TrezorDevice -> {
                        createTrezorKitInstance(account)
                    }

                    is AccountType.BitcoinAddress,
                    is AccountType.EvmAddress,
                    is AccountType.EvmPrivateKey,
                    is AccountType.HdExtendedKey,
                    is AccountType.MnemonicMonero,
                    is AccountType.SolanaAddress,
                    is AccountType.TonAddress,
                    is AccountType.TronAddress,
                    is AccountType.ZCashUfvKey -> throw UnsupportedAccountException()
                }
                this.stellarKitWrapper = wrapper
                job = scope.launch {
                    start(account, wrapper)
                }
                useCount = 0
                currentAccount = account
            }

            useCount++
            requireNotNull(this.stellarKitWrapper)
        }

    private fun eventListenerFactory(account: Account): NetworkErrorEventListener.Factory =
        NetworkErrorEventListener.Factory(BlockchainType.Stellar, account.id, networkErrorTracker)

    private fun createTrezorKitInstance(account: Account): StellarKitWrapper {
        val key = runBlocking {
            hardwarePublicKeyStorage.getKeyByBlockchain(account.id, BlockchainType.Stellar)
        } ?: throw UnsupportedException("Trezor does not have a key for Stellar")
        val publicKeyBytes = KeyPair.fromAccountId(key.key.value).publicKey
        val signer = TrezorStellarSigner(
            publicKey = publicKeyBytes,
            derivationPath = key.derivationPath,
            networkPassphrase = org.stellar.sdk.Network.PUBLIC.networkPassphrase,
            trezorClient = trezorClient
        )
        val kit = StellarKit.getInstance(
            signer,
            Network.MainNet,
            App.instance,
            account.id,
            eventListenerFactory(account)
        )
        return StellarKitWrapper(kit)
    }

    private fun createKitInstance(accountType: AccountType, account: Account): StellarKitWrapper {
        val kit = if (accountType is AccountType.HardwareCard) {
            val hardwarePublicKey = runBlocking {
                hardwarePublicKeyStorage.getKey(
                    account.id,
                    BlockchainType.Stellar,
                    TokenType.Native
                )
            } ?: throw UnsupportedException("Hardware card does not have a public key for Stellar")

            val stellarWallet = HardwareWalletStellarSigner(
                hardwarePublicKey = hardwarePublicKey
            )
            StellarKit.getInstance(
                stellarWallet,
                Network.MainNet,
                App.instance,
                account.id,
                eventListenerFactory(account)
            )
        } else {
            StellarKit.getInstance(
                accountType.toStellarWallet(),
                Network.MainNet,
                App.instance,
                account.id,
                eventListenerFactory(account)
            )
        }

        return StellarKitWrapper(kit)
    }

    suspend fun unlink(account: Account) = lifecycleMutex.withLock {
        if (account == currentAccount) {
            useCount -= 1

            if (useCount < 1) {
                stop()
            }
        }
    }

    suspend fun startForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStarted {
            val account = currentAccount
            if (account == null || !isNetworkPaused(account)) {
                stellarKitWrapper?.let { wrapper ->
                    wrapper.stellarKit.start()
                    wrapper.stellarKit.refresh()
                    wrapper.networkStarted = true
                }
            }
        }
    }

    suspend fun stopForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            stellarKitWrapper?.let { wrapper ->
                wrapper.stellarKit.stop()
                wrapper.networkStarted = false
            }
        }
    }

    suspend fun pauseNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = stellarKitWrapper ?: return@withLock
        if (!wrapper.networkStarted) return@withLock
        wrapper.stellarKit.stop()
        wrapper.networkStarted = false
    }

    suspend fun resumeNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = stellarKitWrapper ?: return@withLock
        if (wrapper.networkStarted) return@withLock
        wrapper.stellarKit.start()
        wrapper.networkStarted = true
    }

    // Ownership is invalidated before the suspending teardown, so a cancellation here cannot leave a
    // half-destroyed wrapper published. The teardown runs under NonCancellable and cancels the start
    // job first: a start still in flight would otherwise bring the discarded account's networking
    // back up after teardown.
    private suspend fun stop() {
        val stoppingJob = job
        val wrapper = stellarKitWrapper
        job = null
        stellarKitWrapper = null
        currentAccount = null
        withContext(NonCancellable) {
            stoppingJob?.cancelAndJoin()
            wrapper?.stellarKit?.destroy()
        }
        // NonCancellable does not rethrow on exit, so without this a cancelled account switch would
        // go on to create and start the abandoned account's kit.
        currentCoroutineContext().ensureActive()
    }

    private fun isNetworkPaused(account: Account): Boolean =
        offlineModeManager.isNetworkPaused(account, BlockchainType.Stellar)

    // Runs inside `job` and drives the kit it was created with: a later account switch cancels this
    // job, so the gate can never be evaluated for one account against another account's kit.
    private suspend fun start(account: Account, wrapper: StellarKitWrapper) {
        val kit = wrapper.stellarKit
        if (!isNetworkPaused(account)) {
            kit.start()
            wrapper.networkStarted = true
        }
        backgroundManager.stateFlow.collect { state ->
            if (state == BackgroundManagerState.EnterForeground) {
                if (!isNetworkPaused(account)) {
                    kit.start()
                    wrapper.networkStarted = true
                    delay(1000)
                    if (!isNetworkPaused(account)) kit.refresh()
                }
            } else if (state == BackgroundManagerState.EnterBackground) {
                if (pollingSessionCount.get() == 0 &&
                    !backgroundKeepAliveManager.isKeepAlive(BlockchainType.Stellar)
                ) {
                    kit.stop()
                    wrapper.networkStarted = false
                } else {
                    Timber.tag("TxPoller").d("StellarKit staying alive")
                }
            }
        }
    }

    fun getAddress(account: Account): String = when (account.type) {
        is AccountType.HardwareCard -> {
            val hardwarePublicKey = runBlocking {
                hardwarePublicKeyStorage.getKeyByBlockchain(account.id, BlockchainType.Stellar)
            } ?: throw UnsupportedException("Hardware card does not have a public key for Stellar")
            val stellarWallet = HardwareWalletStellarSigner(hardwarePublicKey = hardwarePublicKey)
            StellarKit.getInstance(stellarWallet, Network.MainNet, App.instance, account.id).receiveAddress
        }

        is AccountType.TrezorDevice -> {
            val key = runBlocking {
                hardwarePublicKeyStorage.getKeyByBlockchain(account.id, BlockchainType.Stellar)
            } ?: throw UnsupportedException("Trezor does not have a key for Stellar")
            key.key.value
        }

        else -> StellarKit.getAccountId(account.type.toStellarWallet())
    }
}

class StellarKitWrapper(val stellarKit: StellarKit) {
    /** True once [StellarKit.start] has been called and no matching [StellarKit.stop] followed it. */
    var networkStarted: Boolean = false
}

fun StellarKit.statusInfo(): Map<String, Any> =
    buildMap {
        put("Sync State", syncStateFlow.value.toAdapterState())
        put("Operation Sync State", operationsSyncStateFlow.value.toAdapterState())
    }

val StellarAsset.Asset.tokenType
    get() = TokenType.Asset(code, issuer)

fun SyncState.toAdapterState(): AdapterState = when (this) {
    is SyncState.NotSynced -> AdapterState.NotSynced(error)
    is SyncState.Synced -> AdapterState.Synced
    is SyncState.Syncing -> AdapterState.Syncing()
}

fun AccountType.toStellarWallet() = when (this) {
    is AccountType.Mnemonic -> StellarWallet.Seed(seed)
    is AccountType.StellarAddress -> StellarWallet.WatchOnly(address)
    is AccountType.StellarSecretKey -> StellarWallet.SecretKey(key)
    else -> throw IllegalArgumentException(
        "Account type ${this.javaClass.simpleName} can not be converted to StellarWallet.Wallet"
    )
}

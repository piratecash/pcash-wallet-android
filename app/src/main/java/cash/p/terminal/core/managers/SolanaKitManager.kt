package cash.p.terminal.core.managers

import android.util.Log
import cash.p.terminal.core.App
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.tangem.signer.HardwareWalletSolanaAccountSigner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import cash.z.ecc.android.sdk.ext.fromHex
import com.solana.core.PublicKey
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Base58
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.SolanaKit
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SolanaKitManager(
    private val rpcSourceManager: SolanaRpcSourceManager,
    private val walletManager: SolanaWalletManager,
    private val backgroundManager: BackgroundManager,
    private val hardwarePublicKeyStorage: HardwarePublicKeyStorage
) {

    private companion object {
        // Temporary limits to avoid too many requests problem in solan sdk
        const val limitFirstTimeTransactionCount: Int = 2
        const val limitTimeTransactionCount: Int = 2
    }

    private val coroutineScope =
        CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.d("SolanaKitManager", "Coroutine error", throwable)
        })
    private var backgroundEventListenerJob: Job? = null
    private var rpcUpdatedJob: Job? = null
    private var tokenAccountJob: Job? = null

    var solanaKitWrapper: SolanaKitWrapper? = null

    private var useCount = 0
    var currentAccount: Account? = null
        private set
    private val solanaKitStoppedSubject = PublishSubject.create<Unit>()

    private val mutex = Mutex()

    val kitStoppedObservable: Observable<Unit>
        get() = solanaKitStoppedSubject

    val statusInfo: Map<String, Any>?
        get() = solanaKitWrapper?.solanaKit?.statusInfo()

    private fun handleUpdateNetwork() {
        stopKit()
        solanaKitStoppedSubject.onNext(Unit)
    }

    suspend fun getSolanaKitWrapper(account: Account): SolanaKitWrapper = mutex.withLock {
        if (this.solanaKitWrapper != null && currentAccount != account) {
            stopKit()
            solanaKitWrapper = null
        }

        this.solanaKitWrapper?.let { existingWrapper ->
            useCount++
            return@withLock existingWrapper
        }

        val newWrapper = when (val accountType = account.type) {
            is AccountType.Mnemonic -> {
                createKitInstance(accountType, account)
            }
            is AccountType.SolanaAddress -> {
                createKitInstance(accountType, account)
            }
            is AccountType.HardwareCard -> {
                createKitInstance(account.id)
            }
            else -> throw UnsupportedAccountException()
        }

        this.solanaKitWrapper = newWrapper
        startKit()
        subscribeToEvents()
        useCount = 1
        currentAccount = account

        return@withLock newWrapper
    }

    private fun createKitInstance(
        accountType: AccountType.Mnemonic,
        account: Account
    ): SolanaKitWrapper {
        val seed = accountType.seed
        val address = Signer.address(seed)
        val signer = Signer.getInstance(seed)

        val kit = SolanaKit.getInstance(
            application = App.instance,
            addressString = address,
            rpcSource = rpcSourceManager.rpcSource,
            walletId = account.id,
            limitFirstTimeTransactionCount = limitFirstTimeTransactionCount,
            limitTimeTransactionCount = limitTimeTransactionCount
        )

        return SolanaKitWrapper(kit, signer)
    }

    private fun createKitInstance(
        accountType: AccountType.SolanaAddress,
        account: Account
    ): SolanaKitWrapper {
        val address = accountType.address

        val kit = SolanaKit.getInstance(
            application = App.instance,
            addressString = address,
            rpcSource = rpcSourceManager.rpcSource,
            walletId = account.id,
            limitFirstTimeTransactionCount = limitFirstTimeTransactionCount,
            limitTimeTransactionCount = limitTimeTransactionCount
        )

        return SolanaKitWrapper(kit, null)
    }

    private suspend fun createKitInstance(
        accountId: String
    ): SolanaKitWrapper {
        val hardwarePublicKey = hardwarePublicKeyStorage.getKey(
            accountId,
            BlockchainType.Solana,
            TokenType.Native
        ) ?: throw UnsupportedException("Hardware card does not have a public key for Solana")

        val signer = Signer(
            HardwareWalletSolanaAccountSigner(
                publicKey = PublicKey(hardwarePublicKey.key.value.fromHex()),
                hardwarePublicKey = hardwarePublicKey
            )
        )

        val kit = SolanaKit.getInstance(
            application = App.instance,
            addressString = Base58.encode(hardwarePublicKey.key.value.fromHex()),
            rpcSource = rpcSourceManager.rpcSource,
            walletId = accountId,
            limitFirstTimeTransactionCount = limitFirstTimeTransactionCount,
            limitTimeTransactionCount = limitTimeTransactionCount
        )

        return SolanaKitWrapper(kit, signer)
    }

    suspend fun unlink(account: Account) = mutex.withLock {
        if (account == currentAccount) {
            useCount -= 1

            if (useCount < 1) {
                stopKit()
            }
        }
    }

    private fun stopKit() {
        solanaKitWrapper?.solanaKit?.stop()
        solanaKitWrapper = null
        currentAccount = null
        tokenAccountJob?.cancel()
        backgroundEventListenerJob?.cancel()
        rpcUpdatedJob?.cancel()
    }

    private fun startKit() {
        solanaKitWrapper?.solanaKit?.let { kit ->
            tokenAccountJob = coroutineScope.launch {
                kit.start()
                kit.fungibleTokenAccountsFlow.collect {
                    walletManager.add(it)
                }
            }
        }
    }

    private fun subscribeToEvents() {
        backgroundEventListenerJob = coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    startKit()
                } else if (state == BackgroundManagerState.EnterBackground) {
                    stopKit()
                }
            }
        }
        rpcUpdatedJob = coroutineScope.launch {
            rpcSourceManager.rpcSourceUpdateObservable.asFlow().collect {
                handleUpdateNetwork()
            }
        }
    }
}

class SolanaKitWrapper(val solanaKit: SolanaKit, val signer: Signer?)
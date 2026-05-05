package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.stellarkit.TagQuery
import io.horizontalsystems.stellarkit.room.Operation
import io.horizontalsystems.stellarkit.room.StellarAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class StellarAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val stellarKitManager: StellarKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val marketKit: MarketKitWrapper,
) {
    private val blockchainType: BlockchainType = BlockchainType.Stellar
    private val logger = AppLogger("stellar-account-manager")
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val singleDispatcherCoroutineScope = CoroutineScope(singleDispatcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var transactionSubscriptionJob: Job? = null

    fun start() {
        singleDispatcherCoroutineScope.launch {
            stellarKitManager.kitStartedFlow.collect { started ->
                handleStarted(started)
            }
        }
    }

    private fun handleStarted(started: Boolean) {
        try {
            if (started) {
                subscribeToTransactions()
            } else {
                stop()
            }
        } catch (exception: Exception) {
            logger.warning("error", exception)
        }
    }

    private fun stop() {
        transactionSubscriptionJob?.cancel()
    }

    private fun subscribeToTransactions() {
        val stellarKitWrapper = stellarKitManager.stellarKitWrapper ?: return
        val account = accountManager.activeAccount ?: return

        transactionSubscriptionJob = coroutineScope.launch {
            stellarKitWrapper.stellarKit.operationFlow(TagQuery(null, null, null))
                .collect { (operations, initial) ->
                    handle(operations, account, initial)
                }
        }
    }

    private suspend fun handle(
        operations: List<Operation>,
        account: Account,
        initial: Boolean,
    ) {
        val shouldAutoEnableTokens = tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

        if (initial && account.origin == AccountOrigin.Restored && !account.isWatchAccount && !shouldAutoEnableTokens) {
            return
        }

        val assets = mutableSetOf<StellarAsset.Asset>()

        operations.forEach { operation ->
            operation.payment?.let { payment ->
                val stellarAsset = payment.asset
                if (stellarAsset is StellarAsset.Asset) {
                    assets.add(stellarAsset)
                }
            }
        }

        handle(assets, account)
    }

    internal suspend fun handle(assets: Set<StellarAsset.Asset>, account: Account) {
        if (assets.isEmpty()) return

        val existingTokenTypeIds = walletManager.activeWallets.map { it.token.type.id }
        val newAssetTypes = assets
            .map { TokenType.Asset(it.code, it.issuer) }
            .filter { it.id !in existingTokenTypeIds }

        if (newAssetTypes.isEmpty()) return

        val tokenInfos = try {
            val queries = newAssetTypes.map { TokenQuery(blockchainType, it) }
            val knownTokens = marketKit.tokensChunked(queries)
            filterKnownAutoEnableTokens(newAssetTypes, knownTokens)
        } catch (ex: Exception) {
            logger.warning("marketKit lookup failed", ex)
            return
        }

        if (tokenInfos.isEmpty()) return

        val enabledWallets = tokenInfos.toEnabledWallets(
            accountId = account.id,
            blockchainType = blockchainType,
            userDeletedWalletManager = userDeletedWalletManager,
        )

        if (enabledWallets.isEmpty()) return

        walletManager.saveEnabledWallets(enabledWallets)
    }
}

package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.TagQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class TonAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val tonKitManager: TonKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val marketKit: MarketKitWrapper,
) {
    private val blockchainType: BlockchainType = BlockchainType.Ton
    private val logger = AppLogger("ton-account-manager")
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val singleDispatcherCoroutineScope = CoroutineScope(singleDispatcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var transactionSubscriptionJob: Job? = null

    fun start() {
        singleDispatcherCoroutineScope.launch {
            tonKitManager.kitStartedFlow.collect { started ->
                handleStarted(started)
            }
        }
    }

    private suspend fun handleStarted(started: Boolean) {
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

    private suspend fun subscribeToTransactions() {
        val tonKitWrapper = tonKitManager.tonKitWrapper ?: return
        val account = accountManager.activeAccount ?: return

        transactionSubscriptionJob = coroutineScope.launch {
            tonKitWrapper.tonKit.eventFlow(TagQuery(null, null, null, null))
                .collect { (events, initial) ->
                    handle(events, account, tonKitWrapper, initial)
                }
        }
    }

    private suspend fun handle(
        events: List<Event>,
        account: Account,
        tonKitWrapper: TonKitWrapper,
        initial: Boolean,
    ) {
        val shouldAutoEnableTokens = tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

        if (initial && account.origin == AccountOrigin.Restored && !account.isWatchAccount && !shouldAutoEnableTokens) {
            return
        }

        val address = tonKitWrapper.tonKit.receiveAddress

        val jettons = mutableSetOf<Jetton>()

        events.forEach { event ->
            event.actions.forEach { action ->
                action.jettonTransfer?.let {
                    if (it.recipient?.address == address) {
                        jettons.add(it.jetton)
                    }
                }
                action.jettonMint?.let {
                    if (it.recipient.address == address) {
                        jettons.add(it.jetton)
                    }
                }
                action.jettonSwap?.let {
                    it.jettonMasterIn?.let { jetton ->
                        jettons.add(jetton)
                    }
                }
            }
        }

        handle(jettons, account)
    }

    internal suspend fun handle(jettons: Set<Jetton>, account: Account) {
        if (jettons.isEmpty()) return

        val existingTokenTypeIds = walletManager.activeWallets.map { it.token.type.id }
        val newJettonTypes = jettons
            .map { it.tokenType }
            .filter { it.id !in existingTokenTypeIds }

        if (newJettonTypes.isEmpty()) return

        val tokenInfos = try {
            val queries = newJettonTypes.map { TokenQuery(blockchainType, it) }
            val knownTokens = marketKit.tokensChunked(queries)
            filterKnownAutoEnableTokens(newJettonTypes, knownTokens)
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

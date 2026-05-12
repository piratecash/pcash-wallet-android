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
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.decoration.NativeTransactionDecoration
import io.horizontalsystems.tronkit.decoration.UnknownTransactionDecoration
import io.horizontalsystems.tronkit.decoration.trc20.Trc20TransferEvent
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.models.TransferContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.util.concurrent.Executors

class TronAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val marketKit: MarketKitWrapper,
    private val tronKitManager: TronKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager
) {
    private val logger = AppLogger("tron-account-manager")
    private val blockchainType = BlockchainType.Tron
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val singleDispatcherCoroutineScope = CoroutineScope(singleDispatcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var transactionSubscriptionJob: Job? = null

    fun start() {
        singleDispatcherCoroutineScope.launch {
            tronKitManager.kitStartedFlow
                .collect { started ->
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
        val tronKitWrapper = tronKitManager.tronKitWrapper ?: return
        val account = accountManager.activeAccount ?: return

        transactionSubscriptionJob = coroutineScope.launch {
            tronKitWrapper.tronKit.transactionsFlow
                .collect { (fullTransactions, initial) ->
                    handle(fullTransactions, account, tronKitWrapper, initial)
                }
        }
    }

    private suspend fun handle(
        fullTransactions: List<FullTransaction>,
        account: Account,
        tronKitWrapper: TronKitWrapper,
        initial: Boolean
    ) {
        val shouldAutoEnableTokens = tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

        if (initial && account.origin == AccountOrigin.Restored && !account.isWatchAccount && !shouldAutoEnableTokens) {
            return
        }

        val address = tronKitWrapper.tronKit.address
        val foundTokenTypes = mutableSetOf<TokenType>()
        val suspiciousTokenTypes = mutableSetOf<TokenType>()

        for (fullTransaction in fullTransactions) {
            when (val decoration = fullTransaction.decoration) {
                is NativeTransactionDecoration -> {
                    when (decoration.contract) {
                        is TransferContract -> {
                            foundTokenTypes.add(TokenType.Native)
                        }

                        else -> {}
                    }
                }

                is UnknownTransactionDecoration -> {
                    if (decoration.internalTransactions.any { it.to == address }) {
                        foundTokenTypes.add(TokenType.Native)
                    }

                    for (event in decoration.events) {
                        if (event !is Trc20TransferEvent) continue

                        if (event.to == address) {
                            val tokenType = TokenType.Eip20(event.contractAddress.base58)

                            if (decoration.fromAddress == address) {
                                foundTokenTypes.add(tokenType)
                            } else {
                                suspiciousTokenTypes.add(tokenType)
                            }
                        }
                    }
                }
            }
        }

        handle(
            foundTokenTypes = foundTokenTypes.toList(),
            suspiciousTokenTypes = suspiciousTokenTypes.minus(foundTokenTypes).toList(),
            account = account,
            tronKit = tronKitWrapper.tronKit
        )
    }

    private suspend fun handle(
        foundTokenTypes: List<TokenType>,
        suspiciousTokenTypes: List<TokenType>,
        account: Account,
        tronKit: TronKit
    ) {
        if (foundTokenTypes.isEmpty() && suspiciousTokenTypes.isEmpty()) return

        try {
            val allTypes = foundTokenTypes + suspiciousTokenTypes
            val queries = allTypes.map { TokenQuery(blockchainType, it) }
            val knownTokens = marketKit.tokensChunked(queries)
            val tokenInfos = filterKnownAutoEnableTokens(allTypes, knownTokens)

            coroutineScope.launch {
                handle(tokenInfos, account, tronKit)
            }
        } catch (ex: Exception) {
            logger.warning("handle failed", ex)
        }
    }

    private suspend fun handle(tokenInfos: List<AutoEnableTokenInfo>, account: Account, tronKit: TronKit) =
        withContext(Dispatchers.IO) {
            val existingWallets = walletManager.activeWallets
            val existingTokenTypeIds = existingWallets.map { it.token.type.id }
            val newTokenInfos = tokenInfos.filter { !existingTokenTypeIds.contains(it.type.id) }

            if (newTokenInfos.isEmpty()) return@withContext

            val tokensWithBalance = newTokenInfos.mapNotNull { tokenInfo ->
                when (val tokenType = tokenInfo.type) {
                    TokenType.Native -> {
                        tokenInfo
                    }

                    is TokenType.Eip20 -> {
                        if (tronKit.getTrc20Balance(tokenType.address) > BigInteger.ZERO) {
                            tokenInfo
                        } else {
                            null
                        }
                    }

                    else -> {
                        null
                    }
                }
            }

            val enabledWallets = tokensWithBalance.toEnabledWallets(
                accountId = account.id,
                blockchainType = blockchainType,
                userDeletedWalletManager = userDeletedWalletManager,
            )

            if (isActive) {
                walletManager.saveEnabledWallets(enabledWallets)
            }
        }

}

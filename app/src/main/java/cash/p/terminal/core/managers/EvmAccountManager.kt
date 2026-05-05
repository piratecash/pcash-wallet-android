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
import io.horizontalsystems.erc20kit.core.DataProvider
import io.horizontalsystems.erc20kit.events.TransferEventInstance
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.decorations.IncomingDecoration
import io.horizontalsystems.ethereumkit.decorations.UnknownTransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.oneinchkit.decorations.OneInchDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchSwapDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchUnknownDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchUnoswapDecoration
import io.horizontalsystems.uniswapkit.decorations.SwapDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.util.concurrent.Executors

class EvmAccountManager(
    private val blockchainType: BlockchainType,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val marketKit: MarketKitWrapper,
    private val evmKitManager: EvmKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager
) {
    private val logger = AppLogger("evm-account-manager")
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val singleDispatcherCoroutineScope = CoroutineScope(singleDispatcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var transactionSubscriptionJob: Job? = null

    init {
        singleDispatcherCoroutineScope.launch {
            evmKitManager.kitStartedObservable
                .asFlow()
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
        val evmKitWrapper = evmKitManager.evmKitWrapper ?: return
        val account = accountManager.activeAccount ?: return

        transactionSubscriptionJob = coroutineScope.launch {
            evmKitWrapper.evmKit.allTransactionsFlowable.asFlow().cancellable()
                .collect { (fullTransactions, initial) ->
                    handle(fullTransactions, account, evmKitWrapper, initial)
                }
        }
    }

    private suspend fun handle(
        fullTransactions: List<FullTransaction>,
        account: Account,
        evmKitWrapper: EvmKitWrapper,
        initial: Boolean
    ) {
        val shouldAutoEnableTokens = tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

        if (initial && account.origin == AccountOrigin.Restored && !account.isWatchAccount && !shouldAutoEnableTokens) {
            return
        }

        val address = evmKitWrapper.evmKit.receiveAddress

        val foundTokenTypes = mutableSetOf<TokenType>()
        val suspiciousTokenTypes = mutableSetOf<TokenType>()

        for (fullTransaction in fullTransactions) {
            when (val decoration = fullTransaction.decoration) {
                is IncomingDecoration -> {
                    foundTokenTypes.add(TokenType.Native)
                }

                is SwapDecoration -> {
                    val tokenOut = decoration.tokenOut
                    if (tokenOut is SwapDecoration.Token.Eip20Coin) {
                        foundTokenTypes.add(TokenType.Eip20(tokenOut.address.hex.lowercase()))
                    }
                }

                is OneInchSwapDecoration -> {
                    val tokenOut = decoration.tokenOut
                    if (tokenOut is OneInchDecoration.Token.Eip20Coin) {
                        foundTokenTypes.add(TokenType.Eip20(tokenOut.address.hex.lowercase()))
                    }
                }

                is OneInchUnoswapDecoration -> {
                    val tokenOut = decoration.tokenOut
                    if (tokenOut is OneInchDecoration.Token.Eip20Coin) {
                        foundTokenTypes.add(TokenType.Eip20(tokenOut.address.hex.lowercase()))
                    }
                }

                is OneInchUnknownDecoration -> {
                    val tokenOut = decoration.tokenAmountOut?.token
                    if (tokenOut is OneInchDecoration.Token.Eip20Coin) {
                        foundTokenTypes.add(TokenType.Eip20(tokenOut.address.hex.lowercase()))
                    }
                }

                is UnknownTransactionDecoration -> {
                    if (decoration.internalTransactions.any { it.to == address }) {
                        foundTokenTypes.add(TokenType.Native)
                    }

                    for (eventInstance in decoration.eventInstances) {
                        if (eventInstance !is TransferEventInstance) continue

                        if (eventInstance.to == address) {
                            val tokenType =
                                TokenType.Eip20(eventInstance.contractAddress.hex.lowercase())

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
            evmKit = evmKitWrapper.evmKit
        )
    }

    private suspend fun handle(
        foundTokenTypes: List<TokenType>,
        suspiciousTokenTypes: List<TokenType>,
        account: Account,
        evmKit: EthereumKit
    ) {
        if (foundTokenTypes.isEmpty() && suspiciousTokenTypes.isEmpty()) return

        try {
            val allTypes = foundTokenTypes + suspiciousTokenTypes
            val queries = allTypes.map { TokenQuery(blockchainType, it) }
            val knownTokens = marketKit.tokensChunked(queries)
            val tokenInfos = filterKnownAutoEnableTokens(allTypes, knownTokens)
            coroutineScope.launch {
                handle(tokenInfos, account, evmKit)
            }
        } catch (ex: Exception) {
            logger.warning("handle failed", ex)
        }
    }

    private suspend fun handle(tokenInfos: List<AutoEnableTokenInfo>, account: Account, evmKit: EthereumKit) =
        withContext(Dispatchers.IO) {

            val existingWallets = walletManager.activeWallets
            val existingTokenTypeIds = existingWallets.map { it.token.type.id }
            val newTokenInfos = tokenInfos.filter { !existingTokenTypeIds.contains(it.type.id) }

            if (newTokenInfos.isEmpty()) return@withContext

            val userAddress = evmKit.receiveAddress
            val dataProvider = DataProvider(evmKit)

            val requests = newTokenInfos.map { tokenInfo ->
                async {
                    when (val type = tokenInfo.type) {
                        is TokenType.Native -> tokenInfo
                        is TokenType.Eip20 -> {
                            val contractAddress = try {
                                Address(type.address)
                            } catch (ex: Exception) {
                                null
                            } ?: return@async null
                            val balance = try {
                                dataProvider.getBalance(contractAddress, userAddress).await()
                            } catch (error: Throwable) {
                                null
                            }
                            if (balance == null || balance > BigInteger.ZERO) tokenInfo else null
                        }

                        else -> null
                    }
                }
            }

            val enabledWallets = requests.awaitAll().filterNotNull()
                .toEnabledWallets(
                    accountId = account.id,
                    blockchainType = blockchainType,
                    userDeletedWalletManager = userDeletedWalletManager,
                )

            walletManager.saveEnabledWallets(enabledWallets)
        }

}

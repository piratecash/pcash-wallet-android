package cash.p.terminal.modules.multiswap.providers.changenow

import cash.p.terminal.R
import cash.p.terminal.core.extractBigDecimal
import cash.p.terminal.core.storage.ChangeNowTransactionsStorage
import cash.p.terminal.entities.ChangeNowTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapDepositTooSmall
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteChangeNow
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipientExtended
import cash.p.terminal.network.changenow.data.entity.BackendChangeNowResponseError
import cash.p.terminal.network.changenow.data.entity.request.NewTransactionRequest
import cash.p.terminal.network.changenow.domain.entity.ChangeNowCurrency
import cash.p.terminal.network.changenow.domain.entity.NewTransactionResponse
import cash.p.terminal.network.changenow.domain.repository.ChangeNowRepository
import cash.p.terminal.network.pirate.domain.useCase.GetChangeNowAssociatedCoinTickerUseCase
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class ChangeNowProvider(
    private val walletUseCase: WalletUseCase,
    private val changeNowRepository: ChangeNowRepository,
    private val getChangeNowAssociatedCoinTickerUseCase: GetChangeNowAssociatedCoinTickerUseCase,
    private val changeNowTransactionsStorage: ChangeNowTransactionsStorage
) : IMultiSwapProvider {
    override val id = "changenow"
    override val title = "ChangeNow"
    override val url = ""
    override val icon = R.drawable.ic_change_now
    override val priority = 0

    private val currencies = mutableListOf<ChangeNowCurrency>()
    private var minAmount: BigDecimal? = null
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + coroutineExceptionHandler)

    private var changeNowTransaction: ChangeNowTransaction? = null

    init {
        loadCurrencies()
    }

    override suspend fun supports(tokenFrom: Token, tokenTo: Token) =
        supports(tokenFrom) && supports(tokenTo)

    private fun loadCurrencies() {
        coroutineScope.launch {
            currencies.clear()
            currencies.addAll(changeNowRepository.getAvailableCurrencies().sortedBy { it.ticker })
        }
    }

    override suspend fun supports(token: Token): Boolean =
        withContext(coroutineScope.coroutineContext) {
            getChangeNowTicker(token)?.let {
                isChangeNowTickerActive(it)
            } != null
        }

    private suspend fun getChangeNowTicker(token: Token): String? =
        getChangeNowAssociatedCoinTickerUseCase.invoke(
            token.coin.uid,
            token.blockchainType.uid
        )

    private fun isChangeNowTickerActive(ticker: String): String? = currencies.find {
        it.ticker == ticker
    }?.ticker

    private suspend fun getExchangeAmountOrThrow(
        tickerFrom: String,
        tickerTo: String,
        amountIn: BigDecimal
    ): BigDecimal? {
        return try {
            changeNowRepository.getExchangeAmount(
                tickerFrom = tickerFrom,
                tickerTo = tickerTo,
                amount = amountIn
            ).estimatedAmount
        } catch (e: BackendChangeNowResponseError) {
            //extract decimal from message
            if (e.error == BackendChangeNowResponseError.DEPOSIT_TOO_SMALL) {
                val amount = e.message.extractBigDecimal() ?: throw e
                throw SwapDepositTooSmall(amount)
            } else {
                throw e
            }
        } catch (e: Exception) {
            throw IllegalStateException("ChangeNowProvider: error fetching amount", e)
        }
    }

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>
    ): ISwapQuote = withContext(coroutineScope.coroutineContext) {
        val (tickerIn, tickerOut) = awaitAll(
            async { getChangeNowTicker(tokenIn) },
            async { getChangeNowTicker(tokenOut) }
        )

        val tickerFrom = tickerIn
            ?: throw IllegalStateException("ChangeNowProvider: ticker for $tokenIn is not found")
        val tickerTo = tickerOut
            ?: throw IllegalStateException("ChangeNowProvider: ticker for $tokenOut is not found")

        minAmount = changeNowRepository.getMinAmount(
            tickerFrom = tickerFrom,
            tickerTo = tickerTo
        ).also {
            if (it > amountIn) {
                throw SwapDepositTooSmall(it)
            }
        }

        val amountOut = getExchangeAmountOrThrow(tickerFrom, tickerTo, amountIn)
            ?: throw IllegalStateException("ChangeNowProvider: amount is not found")

        val actionRequired = getActionRequired(tokenIn, tokenOut)

        SwapQuoteChangeNow(
            amountOut = amountOut,
            priceImpact = null,
            fields = emptyList(),
            settings = emptyList(),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = actionRequired
        )
    }

    private fun getActionRequired(
        tokenIn: Token,
        tokenOut: Token
    ): ActionCreate? {
        val tokenInWalletCreated = walletUseCase.getWallet(tokenIn) != null
        val tokenOutWalletCreated = walletUseCase.getWallet(tokenOut) != null
        return if (!tokenInWalletCreated || !tokenOutWalletCreated) {
            ActionCreate(
                inProgress = false,
                onActionExecuted = { onActionCompleted ->
                    if (!tokenInWalletCreated) {
                        walletUseCase.createWallet(tokenIn)
                    }
                    if (!tokenOutWalletCreated) {
                        walletUseCase.createWallet(tokenOut)
                    }
                    onActionCompleted()
                }
            )
        } else {
            null
        }
    }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?
    ): ISwapFinalQuote = withContext(coroutineScope.coroutineContext) {
        val transaction: NewTransactionResponse = try {
            val (tickerIn, tickerOut) = awaitAll(
                async { getChangeNowTicker(tokenIn) },
                async { getChangeNowTicker(tokenOut) }
            )
            changeNowRepository.createTransaction(
                newTransactionRequest = NewTransactionRequest(
                    from = tickerIn!!,
                    to = tickerOut!!,
                    amount = amountIn.toPlainString(),
                    address = walletUseCase.getReceiveAddress(tokenOut),
                    refundAddress = walletUseCase.getReceiveAddress(tokenIn)
                )
            )
        } catch (e: BackendChangeNowResponseError) {
            //extract decimal from message
            if (e.error == BackendChangeNowResponseError.OUT_OF_RANGE) {
                val amount = e.message.extractBigDecimal() ?: throw e
                return@withContext SwapFinalQuoteEvm(
                    tokenIn = tokenIn,
                    tokenOut = tokenOut,
                    amountIn = amountIn,
                    amountOut = BigDecimal.ZERO,
                    amountOutMin = amount,
                    sendTransactionData = SendTransactionData.Common(
                        amount = amountIn,
                        address = "",
                        token = tokenIn
                    ),
                    priceImpact = null,
                    fields = emptyList()
                )
            } else {
                throw e
            }
        } catch (e: Exception) {
            throw IllegalStateException("ChangeNowProvider: error fetchFinalQuote", e)
        }

        val fields = buildList {
            add(
                DataFieldRecipientExtended(
                    address = cash.p.terminal.entities.Address(transaction.payinAddress),
                    blockchainType = tokenOut.blockchainType
                )
            )
        }

        changeNowTransaction = ChangeNowTransaction(
            date = System.currentTimeMillis(),
            transactionId = transaction.id,
            coinUidIn = tokenIn.coin.uid,
            blockchainTypeIn = tokenIn.blockchainType.uid,
            amountIn = amountIn,
            addressIn = walletUseCase.getReceiveAddress(tokenIn),
            coinUidOut = tokenOut.coin.uid,
            blockchainTypeOut = tokenOut.blockchainType.uid,
            amountOut = transaction.amount,
            addressOut = walletUseCase.getReceiveAddress(tokenOut)
        )

        SwapFinalQuoteEvm(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = transaction.amount,
            amountOutMin = transaction.amount,
            sendTransactionData = SendTransactionData.Common(
                amount = amountIn,
                address = transaction.payinAddress,
                token = tokenIn
            ),
            priceImpact = null,
            fields = fields
        )
    }

    fun onTransactionCompleted() {
        changeNowTransaction?.let {
            changeNowTransactionsStorage.save(it)
        }
    }
}

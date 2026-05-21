package cash.p.terminal.modules.paycore

import cash.p.terminal.R
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal

class PayCoreProvider(
    override val walletUseCase: WalletUseCase,
    private val apiService: PayCoreApiService,
    private val secureStorage: PayCoreSecureStorage,
    private val featureToggle: PayCoreFeatureToggle,
    private val accountManager: IAccountManager,
    private val adapterManager: IAdapterManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val dispatcherProvider: DispatcherProvider,
) : IMultiSwapProvider {

    private var swapProviderTransaction: SwapProviderTransaction? = null

    override val id = "paycore"
    override val title = "PayCore"
    override val icon = R.drawable.ic_paycore
    override val mevProtectionAvailable = false

    override suspend fun supports(tokenFrom: Token, tokenTo: Token): Boolean {
        if (!featureToggle.isEnabled()) return false
        if (!isAccountCapable()) return false

        val fromIsRub = PayCoreAssets.isRub(tokenFrom)
        val toIsRub = PayCoreAssets.isRub(tokenTo)
        val fromIsUsdt = PayCoreNetworkMapper.isUsdtOnSupportedNetwork(tokenFrom)
        val toIsUsdt = PayCoreNetworkMapper.isUsdtOnSupportedNetwork(tokenTo)

        return (fromIsUsdt && toIsRub) || (fromIsRub && toIsUsdt)
    }

    override suspend fun supports(token: Token): Boolean {
        if (!featureToggle.isEnabled()) return false
        if (!isAccountCapable()) return false
        return PayCoreAssets.isRub(token) || PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token)
    }

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>
    ): ISwapQuote {
        val networkType = resolveNetworkType(tokenIn, tokenOut)
        val (currencyFrom, currencyTo) = resolveCurrencyPair(tokenIn, networkType)
        val rateResponse = try {
            apiService.getRate(currencyFrom, currencyTo, amountFrom = amountIn.toPlainString())
        } catch (e: PayCoreAmountOutOfRangeException) {
            throw SwapAmountOutOfRange()
        }

        val amountOut = rateResponse.amountTo.toBigDecimal()

        val fields = buildQuoteFields(networkType)
        val actionRequired = resolveActionRequired(tokenIn, tokenOut)

        return PayCoreQuote(
            amountOut = amountOut,
            priceImpact = null,
            fields = fields,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = actionRequired
        )
    }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote
    ): ISwapFinalQuote {
        val networkType = resolveNetworkType(tokenIn, tokenOut)
        val (currencyFrom, currencyTo) = resolveCurrencyPair(tokenIn, networkType)
        val rateResponse = try {
            apiService.getRate(currencyFrom, currencyTo, amountFrom = amountIn.toPlainString())
        } catch (e: PayCoreAmountOutOfRangeException) {
            throw SwapAmountOutOfRange()
        }
        val amountOut = rateResponse.amountTo.toBigDecimal()

        val payoutResponse = apiService.getPayoutAddress(
            PayCorePayoutAddressRequest(networkType = networkType)
        )

        val sendTransactionData = buildTransactionData(
            tokenIn = tokenIn,
            amountIn = amountIn,
            depositAddress = payoutResponse.address
        )

        swapProviderTransaction = SwapProviderTransaction(
            date = System.currentTimeMillis(),
            outgoingRecordUid = null,
            transactionId = "",
            status = TransactionStatusEnum.NEW.name.lowercase(),
            provider = SwapProvider.PAYCORE,
            coinUidIn = tokenIn.coin.uid,
            blockchainTypeIn = tokenIn.blockchainType.uid,
            amountIn = amountIn,
            addressIn = tryOrNull { walletUseCase.getReceiveAddress(tokenIn) }.orEmpty(),
            coinUidOut = tokenOut.coin.uid,
            blockchainTypeOut = tokenOut.blockchainType.uid,
            amountOut = amountOut,
            addressOut = tryOrNull { walletUseCase.getReceiveAddress(tokenOut) }.orEmpty(),
            accountId = accountManager.activeAccount?.id.orEmpty(),
        )

        return PayCoreFinalQuote(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = amountOut,
            sendTransactionData = sendTransactionData,
            priceImpact = null,
            fields = buildQuoteFields(networkType),
            payCoreTransactionId = null
        )
    }

    private fun resolveNetworkType(tokenIn: Token, tokenOut: Token): String {
        val usdtToken = if (PayCoreAssets.isRub(tokenIn)) tokenOut else tokenIn
        return requireNotNull(PayCoreNetworkMapper.toNetworkType(usdtToken)) {
            "Unsupported network for token: $usdtToken"
        }
    }

    private fun resolveCurrencyPair(tokenIn: Token, networkType: String): Pair<String, String> {
        return if (PayCoreAssets.isRub(tokenIn)) {
            "RUB" to networkType
        } else {
            networkType to "RUB"
        }
    }

    private fun buildQuoteFields(networkType: String): List<DataField> {
        val fee = PayCoreFees.forNetwork(networkType)
        return buildList {
            if (fee > BigDecimal.ZERO) {
                add(PayCoreDataFieldServiceFee(fee = fee, networkType = networkType))
            }
            add(PayCoreDataFieldNetwork(networkType = networkType))
        }
    }

    private fun resolveActionRequired(tokenIn: Token, tokenOut: Token): ISwapProviderAction? {
        val createAction = getCreateTokenActionRequired(
            listOf(tokenIn, tokenOut).filterNot(PayCoreAssets::isFiat)
        )
        if (createAction != null) return createAction

        val status = secureStorage.getVerificationStatus()
        if (status == VerificationStatus.VERIFIED) return null

        return PayCoreVerificationAction()
    }

    private fun buildTransactionData(
        tokenIn: Token,
        amountIn: BigDecimal,
        depositAddress: String
    ): SendTransactionData {
        return when {
            tokenIn.blockchainType.isEvm -> {
                val adapter = adapterManager.getAdapterForToken<ISendEthereumAdapter>(tokenIn)
                    ?: error("Ethereum adapter not found for $tokenIn")

                val transactionData = adapter.getTransactionData(
                    amountIn,
                    Address(depositAddress)
                )
                SendTransactionData.Evm(transactionData, null, amount = amountIn)
            }

            tokenIn.blockchainType == BlockchainType.Tron -> {
                SendTransactionData.Tron.Regular(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            else -> SendTransactionData.Unsupported
        }
    }

    override fun onTransactionCompleted(result: SendTransactionResult) {
        val pending = swapProviderTransaction ?: return
        val txHash = result.getRecordUid() ?: return
        val network = payoutNetworkType(pending)

        // Persist the swap record up-front using txHash as a placeholder
        // transactionId. The blockchain side already succeeded — losing the
        // record because the backend hasn't indexed the tx yet is unacceptable.
        // The real payoutId is migrated in later, either by the retry loop below
        // or by PayCoreStatusRepository on a subsequent status refresh.
        val saved = pending.copy(
            outgoingRecordUid = txHash,
            transactionId = txHash,
            date = System.currentTimeMillis()
        )
        swapProviderTransaction = saved

        dispatcherProvider.applicationScope.launch {
            swapProviderTransactionsStorage.save(saved)
            val payoutId = requestPayoutIdWithRetries(txHash, network)
            if (payoutId == null) {
                Timber.w(
                    "PayCore processPayOut failed after %d attempts (txHash=%s) — record kept with placeholder id, will retry on next status refresh",
                    PAYOUT_RETRY_DELAYS_MS.size, txHash
                )
                return@launch
            }
            migratePayoutId(saved.date, payoutId)
            Timber.d("PayCore payout processed: txHash=%s, payoutId=%s", txHash, payoutId)
        }
    }

    private fun migratePayoutId(date: Long, payoutId: String) {
        swapProviderTransactionsStorage.updateTransactionId(date, payoutId)
        swapProviderTransaction = swapProviderTransaction?.copy(transactionId = payoutId)
    }

    private suspend fun requestPayoutIdWithRetries(txHash: String, network: String): String? {
        PAYOUT_RETRY_DELAYS_MS.forEachIndexed { attempt, delayMs ->
            if (delayMs > 0) delay(delayMs)
            val response = tryOrNull {
                apiService.processPayOut(
                    PayCorePayoutProcessRequest(
                        transactionHash = txHash,
                        backUrl = PAYCORE_COMPLETE_BACK_URL
                    ),
                    networkType = network
                )
            }
            if (response == null) {
                Timber.w(
                    "PayCore processPayOut attempt %d/%d failed (txHash=%s)",
                    attempt + 1, PAYOUT_RETRY_DELAYS_MS.size, txHash
                )
                return@forEachIndexed
            }
            if (response.isTerminalFailure()) {
                Timber.w("PayCore: terminal failure (status=3) on processPayOut for txHash=%s — stopping retries", txHash)
                return null
            }
            val payoutId = response.transactionIdOrNull()
            if (payoutId != null) return payoutId
            Timber.w(
                "PayCore processPayOut attempt %d/%d returned no payoutId (txHash=%s, status=%d)",
                attempt + 1, PAYOUT_RETRY_DELAYS_MS.size, txHash, response.status
            )
        }
        return null
    }

    override fun getProviderTransactionId(): String? = swapProviderTransaction?.transactionId

    private fun payoutNetworkType(transaction: SwapProviderTransaction): String {
        return requireNotNull(PayCoreNetworkMapper.fromBlockchainTypeUid(transaction.blockchainTypeIn)) {
            "Unsupported payout blockchain type: ${transaction.blockchainTypeIn}"
        }
    }

    private fun isAccountCapable(): Boolean {
        val accountType = accountManager.activeAccount?.type ?: return false
        return accountType is AccountType.Mnemonic || accountType is AccountType.EvmPrivateKey
    }

    private companion object {
        val PAYOUT_RETRY_DELAYS_MS = longArrayOf(0L, 1_000L, 3_000L, 8_000L)
    }
}

package cash.p.terminal.modules.paycore

import cash.p.terminal.R
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.paycore.PayCoreNetworkMapper.toTicker
import cash.p.terminal.modules.paycore.PayCoreNetworkMapper.toTicker
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.modules.paycore.selectbank.PayCoreBankNotSelectedException
import cash.p.terminal.modules.paycore.selectbank.PayCoreBankSwapSetting
import cash.p.terminal.modules.paycore.selectbank.PayCoreBanksUnavailableException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

class PayCoreProvider(
    override val walletUseCase: WalletUseCase,
    private val apiService: PayCoreApiService,
    private val banksRepository: PayCoreBanksRepository,
    private val walletApprovalService: PayCoreWalletApprovalService,
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
        val ticker = requireTicker(tokenIn, tokenOut)
        val rateResponse = try {
            apiService.getRate(ticker = ticker, networkType = networkType)
        } catch (e: PayCoreAmountOutOfRangeException) {
            throw SwapAmountOutOfRange()
        }

        val amountOut = estimateAmountOut(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            rateResponse = rateResponse,
        )
        validateRateLimits(
            tokenIn = tokenIn,
            amountIn = amountIn,
            limits = rateResponse.limits,
        )

        val serviceFee = rateResponse.withdrawFee
        val fields = buildQuoteFields(networkType, serviceFee)
        val payoutBanks = payoutBanks(tokenIn, tokenOut, networkType)
        val actionRequired =
            getCreateTokenActionRequired(
                listOf(tokenIn, tokenOut).filterNot(PayCoreAssets::isFiat)
            ) ?: resolveWalletApproveActionRequired(
                tokenIn = tokenIn,
                tokenOut = tokenOut
            ) ?: resolveWalletVerifiedActionRequired()
            ?: resolvePayoutBankAction(
                settings = settings,
                banks = payoutBanks,
            )

        return PayCoreQuote(
            amountOut = amountOut,
            priceImpact = null,
            fields = fields,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            serviceFee = serviceFee,
            actionRequired = actionRequired,
            settings = buildSettings(
                settings = settings,
                banks = payoutBanks,
            ),
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
        require(isPayout(tokenIn, tokenOut)) {
            "PayCore final quote is only used for Crypto -> RUB flow"
        }
        val networkType = resolveNetworkType(tokenIn, tokenOut)
        val payoutCalculation = calculatePayout(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            settings = swapSettings,
        )
        val payout = createPayout(payoutCalculation, networkType)

        val finalAmountIn = payoutCalculation.amountCrypto
        val amountOut = payoutCalculation.fullAmountRub

        val sendTransactionData = buildTransactionData(
            tokenIn = tokenIn,
            amountIn = finalAmountIn,
            depositAddress = payout.address
        )

        swapProviderTransaction = SwapProviderTransaction(
            date = System.currentTimeMillis(),
            outgoingRecordUid = null,
            transactionId = payout.uuid,
            status = TransactionStatusEnum.NEW.name.lowercase(),
            provider = SwapProvider.PAYCORE,
            coinUidIn = tokenIn.coin.uid,
            blockchainTypeIn = tokenIn.blockchainType.uid,
            amountIn = finalAmountIn,
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
            amountIn = finalAmountIn,
            amountOut = amountOut,
            sendTransactionData = sendTransactionData,
            priceImpact = null,
            fields = buildQuoteFields(networkType, serviceFeeFrom(swapQuote)),
            swapProviderTransaction = swapProviderTransaction,
        )
    }

    private fun resolveNetworkType(tokenIn: Token, tokenOut: Token): PayCoreTicker {
        val usdtToken = if (PayCoreAssets.isRub(tokenIn)) tokenOut else tokenIn
        return requireNotNull(usdtToken.toTicker()) {
            "Unsupported network for token: $usdtToken"
        }
    }

    private fun requireTicker(tokenIn: Token, tokenOut: Token): PayCoreTicker {
        val usdtToken = if (PayCoreAssets.isRub(tokenIn)) tokenOut else tokenIn
        return requireNotNull(usdtToken.toTicker()) {
            "Unsupported PayCore ticker for type: $usdtToken"
        }
    }

    private fun estimateAmountOut(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        rateResponse: PayCoreRateResponse,
    ): BigDecimal {
        return if (PayCoreAssets.isRub(tokenIn)) {
            amountIn.divide(rateResponse.buy, tokenOut.decimals, RoundingMode.DOWN)
        } else {
            amountIn.multiply(rateResponse.sell).setScale(tokenOut.decimals, RoundingMode.DOWN)
        }.stripTrailingZeros()
    }

    private fun validateRateLimits(
        tokenIn: Token,
        amountIn: BigDecimal,
        limits: PayCoreRateLimits?,
    ) {
        limits ?: return
        val outOfRange = if (PayCoreAssets.isRub(tokenIn)) {
            amountIn.isOutside(limits.minBuyLimitRub, limits.maxBuyLimitRub)
        } else {
            amountIn.isOutside(limits.minSellLimitUsdt, limits.maxSellLimitUsdt)
        }
        if (outOfRange) throw SwapAmountOutOfRange()
    }

    private fun BigDecimal.isOutside(min: BigDecimal?, max: BigDecimal?): Boolean {
        if (min != null && this < min) return true
        if (max != null && this > max) return true
        return false
    }

    private fun buildQuoteFields(networkType: PayCoreTicker, serviceFee: BigDecimal): List<DataField> {
        return buildList {
            if (serviceFee > BigDecimal.ZERO) {
                add(PayCoreDataFieldServiceFee(fee = serviceFee, networkType = networkType))
            }
            add(PayCoreDataFieldNetwork(networkType = networkType))
        }
    }

    private fun serviceFeeFrom(swapQuote: ISwapQuote): BigDecimal {
        return requireNotNull((swapQuote as? PayCoreQuote)?.serviceFee) {
            "PayCore final quote requires PayCoreQuote"
        }
    }

    private suspend fun calculatePayout(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>,
    ): PayCorePayoutCalculationResponse {
        val ticker = requireTicker(tokenIn, tokenOut)
        val networkType = resolveNetworkType(tokenIn, tokenOut)
        val bank = requireSelectedBank(settings, networkType)
        val request = PayCorePayoutCalculationRequest(
            amount = amountIn,
            amountType = PayCoreAmountType.CRYPTO,
            bankId = bank.id,
            ticker = ticker,
        )
        return try {
            apiService.calculatePayout(request = request, networkType = networkType)
        } catch (e: PayCoreAmountOutOfRangeException) {
            throw SwapAmountOutOfRange()
        }
    }

    private suspend fun createPayout(
        payoutCalculation: PayCorePayoutCalculationResponse,
        networkType: PayCoreTicker,
    ): PayCorePayoutCreateResponse {
        return apiService.createPayout(
            request = PayCorePayoutCreateRequest(uuid = payoutCalculation.uuid),
            networkType = networkType,
        )
    }

    private suspend fun payoutBanks(
        tokenIn: Token,
        tokenOut: Token,
        networkType: PayCoreTicker,
    ): List<PayCoreBankResponse>? {
        if (!isPayout(tokenIn, tokenOut)) return null
        return banksRepository.getBanks(networkType)
    }

    private fun buildSettings(
        settings: Map<String, Any?>,
        banks: List<PayCoreBankResponse>?,
    ): List<ISwapSetting> {
        banks ?: return emptyList()
        return listOf(
            PayCoreBankSwapSetting(
                banks = banks,
                selectedBank = selectedBank(settings, banks),
            )
        )
    }

    private suspend fun resolveWalletVerifiedActionRequired(): ISwapProviderAction? =
        withContext(dispatcherProvider.io) {
            val status = secureStorage.getVerificationStatus()
            if (status == VerificationStatus.VERIFIED) return@withContext null

            return@withContext PayCoreVerificationAction()
        }

    private suspend fun resolveWalletApproveActionRequired(
        tokenIn: Token,
        tokenOut: Token
    ): ISwapProviderAction? = withContext(dispatcherProvider.io) {
        // fiat doesn't have adapter, so here we'll get crypto address
        val cryptoWalletAddress = tryOrNull { walletUseCase.getReceiveAddress(tokenOut) }
            ?: tryOrNull { walletUseCase.getReceiveAddress(tokenIn) }
        try {
            val networkType = resolveNetworkType(tokenIn, tokenOut)
            walletApprovalService.ensureApprovedForSavedPhone(
                walletAddress = cryptoWalletAddress.orEmpty(),
                networkType = networkType
            )
            return@withContext null
        } catch (e: PayCoreWalletNotApprovedException) {
            return@withContext PayCoreVerificationAction()
        }
    }

    private fun resolvePayoutBankAction(
        settings: Map<String, Any?>,
        banks: List<PayCoreBankResponse>?,
    ): ISwapProviderAction? {
        banks ?: return null
        if (selectedBank(settings, banks) != null) return null
        return PayCoreSelectBankAction()
    }

    private suspend fun requireSelectedBank(
        settings: Map<String, Any?>,
        networkType: PayCoreTicker,
    ): PayCoreBankResponse {
        val banks = banksRepository.getBanks(networkType)
        if (banks.isEmpty()) throw PayCoreBanksUnavailableException()
        return selectedBank(settings, banks) ?: throw PayCoreBankNotSelectedException()
    }

    private fun selectedBank(
        settings: Map<String, Any?>,
        banks: List<PayCoreBankResponse>,
    ): PayCoreBankResponse? {
        val selected = PayCoreBankSwapSetting.selectedBank(settings) ?: return null
        return banks.firstOrNull { it.id == selected.id }
    }

    private fun isPayout(tokenIn: Token, tokenOut: Token): Boolean {
        return PayCoreNetworkMapper.isUsdtOnSupportedNetwork(tokenIn) && PayCoreAssets.isRub(
            tokenOut
        )
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

            tokenIn.blockchainType == BlockchainType.Solana -> {
                SendTransactionData.Solana.Regular(
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

        val saved = pending.copy(
            outgoingRecordUid = txHash,
            date = System.currentTimeMillis()
        )
        swapProviderTransaction = saved

        dispatcherProvider.applicationScope.launch {
            swapProviderTransactionsStorage.save(saved)
        }
    }

    override fun getProviderTransactionId(): String? = swapProviderTransaction?.transactionId

    private fun isAccountCapable(): Boolean {
        val accountType = accountManager.activeAccount?.type ?: return false
        return accountType is AccountType.Mnemonic || accountType is AccountType.EvmPrivateKey
    }
}

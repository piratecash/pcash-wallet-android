package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.StonFiGasParams
import cash.p.terminal.modules.multiswap.StonFiSwapData
import cash.p.terminal.modules.multiswap.SwapQuoteStonFi
import cash.p.terminal.modules.multiswap.SwapAmountAccuracy
import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.settings.SwapSettingRecipient
import cash.p.terminal.modules.multiswap.settings.SwapSettingSlippage
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipient
import cash.p.terminal.modules.multiswap.ui.DataFieldSlippage
import cash.p.terminal.network.stonfi.domain.entity.SimulateSwap
import cash.p.terminal.network.stonfi.domain.entity.RouterInfo
import cash.p.terminal.network.stonfi.domain.repository.StonFiRepository
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import com.tonapps.blockchain.ton.extensions.toByteArray
import io.horizontalsystems.core.entities.BlockchainType
import io.ktor.util.encodeBase64
import org.ton.block.AddrStd
import org.ton.cell.Cell
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

class StonFiProvider(
    private val stonFiRepository: StonFiRepository,
    override val walletUseCase: WalletUseCase,
) : IMultiSwapProvider, IExactOutSwapProvider {
    override val id = "stonfi"
    override val title = "STON.fi"
    override val icon = R.drawable.ic_ston_fi
    override val exactOutAccuracy = SwapAmountAccuracy.AtLeast

    override val mevProtectionAvailable: Boolean = false
    // TON native token address
    companion object {
        private const val TON_NATIVE_ADDRESS = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"
        private const val REF_FEE_BPS: Int = 10 // 0.1%
        private val REF_ADDRESS_TON = AppConfigProvider.donateAddresses[BlockchainType.Ton]
        private val SLIPPAGE = BigDecimal("0.5") // 0.5%
    }

    override suspend fun supports(token: Token): Boolean {
        if (token.blockchainType != BlockchainType.Ton) {
            return false
        }

        return try {
            val tokenAddress = getTokenAddress(token)
            stonFiRepository.getAssetByAddress(tokenAddress) != null
        } catch (e: Exception) {
            Timber.d(e, "StonFiProvider: failed to get asset for token ${token.coin.code}")
            false
        }
    }

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>
    ): ISwapQuote = fetchQuote(
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amount = amountIn,
        settings = settings,
        direction = SwapAmountDirection.In,
    )

    override suspend fun supportsExactOut(tokenIn: Token, tokenOut: Token): Boolean =
        supports(tokenIn, tokenOut) && tryOrNull {
            getTokenAddress(tokenIn)
            getTokenAddress(tokenOut)
        } != null

    override suspend fun fetchQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote = fetchQuote(
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amount = amountOut,
        settings = settings,
        direction = SwapAmountDirection.Out,
    )

    private suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        settings: Map<String, Any?>,
        direction: SwapAmountDirection,
    ): ISwapQuote {
        val settingRecipient = SwapSettingRecipient(settings, tokenOut)
        val settingSlippage = SwapSettingSlippage(settings, SLIPPAGE)
        val simulation = simulateSwapWithFallback(
            request = simulationRequest(
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amount = amount,
                slippage = settingSlippage.valueOrDefault(),
                direction = direction,
            ),
            preferredVersions = listOf(2, 1),
        )
        val response = simulation.swap
        val amountIn = response.offerUnits.toBigDecimal().movePointLeft(tokenIn.decimals)
        val amountOut = BigDecimal(response.askUnits).movePointLeft(tokenOut.decimals)

        return SwapQuoteStonFi(
            amountOut = amountOut,
            priceImpact = BigDecimal(response.priceImpact),
            fields = quoteFields(settingRecipient, settingSlippage),
            settings = listOf(settingRecipient, settingSlippage),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut)),
            swapData = swapData(response, simulation.dexVersion),
        )
    }

    private fun swapData(response: SimulateSwap, dexVersion: Int) = StonFiSwapData(
        offerAddress = response.offerAddress,
        askAddress = response.askAddress,
        offerJettonWallet = response.offerJettonWallet,
        askJettonWallet = response.askJettonWallet,
        routerAddress = response.routerAddress,
        poolAddress = response.poolAddress,
        offerUnits = response.offerUnits,
        askUnits = response.askUnits,
        slippageTolerance = response.slippageTolerance,
        minAskUnits = response.minAskUnits,
        swapRate = response.swapRate,
        priceImpact = response.priceImpact,
        feeAddress = response.feeAddress,
        feeUnits = response.feeUnits,
        feePercent = response.feePercent,
        gasParams = StonFiGasParams(
            forwardGas = response.gasParams.forwardGas,
            estimatedGasConsumption = response.gasParams.estimatedGasConsumption,
            gasBudget = response.gasParams.gasBudget,
        ),
        dexVersion = dexVersion,
    )

    private fun quoteFields(
        recipient: SwapSettingRecipient,
        slippage: SwapSettingSlippage,
    ): List<DataField> = buildList {
        recipient.value?.let { add(DataFieldRecipient(it)) }
        slippage.value?.let { add(DataFieldSlippage(it)) }
    }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote
    ): ISwapFinalQuote = fetchFinalQuote(
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amount = amountIn,
        swapSettings = swapSettings,
        swapQuote = swapQuote,
        direction = SwapAmountDirection.In,
    )

    override suspend fun fetchFinalQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote = fetchFinalQuote(
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amount = amountOut,
        swapSettings = swapSettings,
        swapQuote = swapQuote,
        direction = SwapAmountDirection.Out,
    )

    private suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        swapSettings: Map<String, Any?>,
        swapQuote: ISwapQuote,
        direction: SwapAmountDirection,
    ): ISwapFinalQuote {
        check(swapQuote is SwapQuoteStonFi)
        val settingRecipient = SwapSettingRecipient(swapSettings, tokenOut)
        val settingSlippage = SwapSettingSlippage(swapSettings, SLIPPAGE)
        // Get fresh quote for final transaction
        val finalSimulation = simulateFreshFinalSwap(
            tokenIn,
            tokenOut,
            amount,
            settingSlippage.valueOrDefault(),
            direction,
            swapQuote,
        )
        val response = finalSimulation.swap
        val amounts = finalQuoteAmounts(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            targetAmount = amount,
            direction = direction,
            response = response,
        )
        val addressFrom = walletUseCase.getReceiveAddress(tokenIn)
        val receiverOwnerAddress = settingRecipient.value?.hex ?: walletUseCase.getReceiveAddress(tokenOut)
        val routerInfo = stonFiRepository.getRouter(response.routerAddress)
        val ptonWalletAddress = ptonWalletAddress(tokenIn, receiverOwnerAddress, routerInfo)
        val destinationAddress = when {
            finalSimulation.dexVersion == 1 && tokenIn.type == TokenType.Native -> response.offerJettonWallet
                .takeUnless { it.isBlank() }
                ?: error("STON.fi v1: missing offer jetton wallet")
            else -> ptonWalletAddress
        }
        val tonTransferQueryId = System.currentTimeMillis()
        val payload = tonSwapPayload(
            TonSwapPayloadRequest(
                tokenIn = tokenIn,
                amountIn = amounts.amountIn,
                response = response,
                dexVersion = finalSimulation.dexVersion,
                addressFrom = addressFrom,
                receiverOwnerAddress = receiverOwnerAddress,
                routerInfo = routerInfo,
                queryId = tonTransferQueryId,
                minimumAskUnits = amounts.minimumAskUnits,
            )
        )
        val sendTransactionData = sendTransactionData(
            response,
            routerInfo,
            destinationAddress,
            tonTransferQueryId,
            settingSlippage.valueOrDefault(),
            payload,
        )
        return finalQuote(
            amounts = amounts,
            response = response,
            sendTransactionData = sendTransactionData,
            fields = quoteFields(settingRecipient, settingSlippage),
        )
    }

    private suspend fun simulateFreshFinalSwap(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        slippage: BigDecimal,
        direction: SwapAmountDirection,
        swapQuote: SwapQuoteStonFi,
    ) = simulateSwapWithFallback(
        request = simulationRequest(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amount = amount,
            slippage = slippage,
            direction = direction,
            poolAddress = swapQuote.swapData.poolAddress.takeIf(String::isNotBlank),
        ),
        preferredVersions = preferredVersions(swapQuote.swapData.dexVersion),
    )

    private fun finalQuoteAmounts(
        tokenIn: Token,
        tokenOut: Token,
        targetAmount: BigDecimal,
        direction: SwapAmountDirection,
        response: SimulateSwap,
    ): FinalQuoteAmounts {
        val minimumAskUnits = stonFiMinimumAskUnits(
            direction,
            targetAmount,
            tokenOut.decimals,
            response.minAskUnits,
        )
        return FinalQuoteAmounts(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = response.offerUnits.toBigDecimal().movePointLeft(tokenIn.decimals),
            amountOut = BigDecimal(response.askUnits).movePointLeft(tokenOut.decimals),
            minimumAmountOut = minimumAskUnits.toBigDecimal().movePointLeft(tokenOut.decimals),
            minimumAskUnits = minimumAskUnits,
        )
    }

    private fun preferredVersions(preferred: Int): List<Int> =
        if (preferred == 2) listOf(2, 1) else listOf(1, 2)

    private fun sendTransactionData(
        response: SimulateSwap,
        routerInfo: RouterInfo,
        destinationAddress: String?,
        queryId: Long,
        slippage: BigDecimal,
        payload: TonSwapPayload,
    ) = SendTransactionData.TonSwap(
        offerUnits = response.offerUnits,
        forwardGas = response.gasParams.forwardGas,
        routerAddress = response.routerAddress,
        routerMasterAddress = routerInfo.ptonMasterAddress,
        destinationAddress = destinationAddress,
        queryId = queryId,
        slippage = slippage,
        payload = payload.cell.toByteArray().encodeBase64(),
        gasBudget = payload.gasBudget,
    )

    private fun finalQuote(
        amounts: FinalQuoteAmounts,
        response: SimulateSwap,
        sendTransactionData: SendTransactionData,
        fields: List<DataField>,
    ) = SwapFinalQuoteTon(
        tokenIn = amounts.tokenIn,
        tokenOut = amounts.tokenOut,
        amountIn = amounts.amountIn,
        amountOut = amounts.amountOut,
        amountOutMin = amounts.minimumAmountOut,
        sendTransactionData = sendTransactionData,
        priceImpact = BigDecimal(response.priceImpact),
        fields = fields,
    )

    private suspend fun ptonWalletAddress(
        tokenIn: Token,
        receiverOwnerAddress: String,
        routerInfo: RouterInfo,
    ): String? = when (val tokenType = tokenIn.type) {
        is TokenType.Jetton -> tryOrNull {
            stonFiRepository.getJettonAddress(tokenType.address, receiverOwnerAddress)
        }
        else -> routerInfo.ptonWalletAddress
    }

    private fun tonSwapPayload(request: TonSwapPayloadRequest): TonSwapPayload =
        if (request.tokenIn.type is TokenType.Jetton) {
            jettonSwapPayload(request)
        } else {
            nativeTonSwapPayload(request)
        }

    private fun jettonSwapPayload(request: TonSwapPayloadRequest): TonSwapPayload {
        val response = request.response
        val amountUnits = request.amountIn.movePointRight(request.tokenIn.decimals).toBigInteger()
        return when (request.dexVersion) {
            1 -> TonSwapPayload(
                cell = buildJettonToTonPayloadV1(
                    router = AddrStd(response.routerAddress),
                    refundAddress = AddrStd(request.addressFrom),
                    routerPtonWallet = AddrStd(request.routerInfo.ptonWalletAddress),
                    amount = amountUnits,
                    minOut = request.minimumAskUnits,
                    queryId = request.queryId,
                    referralAddress = REF_ADDRESS_TON?.let(::AddrStd),
                    forwardTonAmount = response.gasParams.forwardGas,
                ),
                gasBudget = response.offerUnits + response.gasParams.forwardGas + BigInteger("100000000"),
            )
            2 -> TonSwapPayload(
                cell = buildJettonToTonPayloadV2(
                    amount = amountUnits,
                    router = AddrStd(response.routerAddress),
                    ptonWallet = AddrStd(response.askJettonWallet),
                    refundAddress = AddrStd(request.addressFrom),
                    minOut = request.minimumAskUnits,
                    forwardGas = response.gasParams.forwardGas,
                    queryId = request.queryId,
                    refFee = REF_FEE_BPS,
                    referralAddress = REF_ADDRESS_TON?.let(::AddrStd),
                ),
                gasBudget = response.gasParams.gasBudget,
            )
            else -> error("Unsupported dex version: ${request.dexVersion}")
        }
    }

    private fun nativeTonSwapPayload(request: TonSwapPayloadRequest): TonSwapPayload {
        val response = request.response
        return when (request.dexVersion) {
            1 -> TonSwapPayload(
                cell = buildStonfiSwapTonToJettonTransferV1(
                    amount = request.amountIn.movePointRight(request.tokenIn.decimals).toBigInteger(),
                    routerAddress = AddrStd(response.routerAddress),
                    routerJettonWallet = AddrStd(
                        response.askJettonWallet.takeUnless { it.isNullOrBlank() }
                            ?: error("STON.fi v1: missing ask jetton wallet")
                    ),
                    receiver = AddrStd(request.receiverOwnerAddress),
                    minOut = request.minimumAskUnits,
                    referralAddress = REF_ADDRESS_TON?.let(::AddrStd),
                    forwardTonAmount = response.gasParams.forwardGas,
                    queryId = request.queryId,
                ),
                gasBudget = response.offerUnits + BigInteger("185000000"),
            )
            2 -> TonSwapPayload(
                cell = buildStonfiSwapTonToJettonPayloadV2(
                    tonAmount = response.offerUnits,
                    tokenWallet = AddrStd(response.askJettonWallet),
                    refundAddress = AddrStd(request.addressFrom),
                    minOut = request.minimumAskUnits,
                    receiver = AddrStd(request.receiverOwnerAddress),
                    refFee = REF_FEE_BPS,
                    fwdGas = response.gasParams.forwardGas,
                    referralAddress = REF_ADDRESS_TON?.let(::AddrStd),
                ),
                gasBudget = response.gasParams.gasBudget,
            )
            else -> error("Unsupported dex version: ${request.dexVersion}")
        }
    }

    private fun simulationRequest(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        slippage: BigDecimal,
        direction: SwapAmountDirection,
        poolAddress: String? = null,
    ) = SimulationRequest(
        offerAddress = getTokenAddress(tokenIn),
        askAddress = getTokenAddress(tokenOut),
        units = amount.movePointRight(
            if (direction == SwapAmountDirection.In) tokenIn.decimals else tokenOut.decimals
        ).toBigInteger().toString(),
        slippageTolerance = slippage,
        poolAddress = poolAddress,
        referralAddress = REF_ADDRESS_TON,
        referralFeeBps = REF_ADDRESS_TON?.let { REF_FEE_BPS },
        direction = direction,
    )

    private suspend fun simulateSwapWithFallback(
        request: SimulationRequest,
        preferredVersions: List<Int>,
    ): SimulationResult {
        val errors = mutableListOf<Throwable>()

        preferredVersions.forEachIndexed { index, dexVersion ->
            val attempt = request.copy(poolAddress = if (index == 0) request.poolAddress else null)

            val result = runCatching {
                simulateSwap(attempt, dexVersion)
            }.getOrElse {
                errors.add(it)
                null
            }

            if (result != null) {
                if (result.hasPositiveOutput()) {
                    return SimulationResult(result, dexVersion)
                }

                errors.add(IllegalStateException("STON.fi returned zero output for dex_version=$dexVersion"))
            }
        }

        val cause = errors.lastOrNull()
        throw cause ?: IllegalStateException("Failed to simulate swap on STON.fi")
    }

    private suspend fun simulateSwap(
        request: SimulationRequest,
        dexVersion: Int,
    ): SimulateSwap = when (request.direction) {
        SwapAmountDirection.In -> stonFiRepository.simulateSwap(
            request.offerAddress,
            request.askAddress,
            request.units,
            request.slippageTolerance,
            request.poolAddress,
            request.referralAddress,
            request.referralFeeBps,
            dexVersion,
        )

        SwapAmountDirection.Out -> stonFiRepository.reverseSimulateSwap(
            request.offerAddress,
            request.askAddress,
            request.units,
            request.slippageTolerance,
            request.poolAddress,
            request.referralAddress,
            request.referralFeeBps,
            dexVersion,
        )
    }

    private fun SimulateSwap.hasPositiveOutput(): Boolean {
        val askValue = parsePositiveBigDecimal(askUnits)
        val minAskValue = parseNonNegativeBigDecimal(minAskUnits)
        return askValue != null && minAskValue != null
    }

    private fun parsePositiveBigDecimal(value: String): BigDecimal? =
        tryOrNull { BigDecimal(value) }?.takeIf { it.signum() > 0 }

    private fun parseNonNegativeBigDecimal(value: String): BigDecimal? =
        tryOrNull { BigDecimal(value) }?.takeIf { it.signum() >= 0 }

    private data class SimulationResult(
        val swap: SimulateSwap,
        val dexVersion: Int
    )

    private data class SimulationRequest(
        val offerAddress: String,
        val askAddress: String,
        val units: String,
        val slippageTolerance: BigDecimal,
        val poolAddress: String?,
        val referralAddress: String?,
        val referralFeeBps: Int?,
        val direction: SwapAmountDirection,
    )

    private data class TonSwapPayloadRequest(
        val tokenIn: Token,
        val amountIn: BigDecimal,
        val response: SimulateSwap,
        val dexVersion: Int,
        val addressFrom: String,
        val receiverOwnerAddress: String,
        val routerInfo: RouterInfo,
        val queryId: Long,
        val minimumAskUnits: BigInteger,
    )

    private data class TonSwapPayload(
        val cell: Cell,
        val gasBudget: BigInteger,
    )

    private data class FinalQuoteAmounts(
        val tokenIn: Token,
        val tokenOut: Token,
        val amountIn: BigDecimal,
        val amountOut: BigDecimal,
        val minimumAmountOut: BigDecimal,
        val minimumAskUnits: BigInteger,
    )

    private fun getTokenAddress(token: Token): String {
        return when (val tokenType = token.type) {
            TokenType.Native -> TON_NATIVE_ADDRESS
            is TokenType.Jetton -> tokenType.address
            else -> throw IllegalArgumentException("Unsupported token type for STON.fi: $tokenType")
        }
    }
}

class SwapFinalQuoteTon(
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val amountOut: BigDecimal,
    override val amountOutMin: BigDecimal?,
    override val sendTransactionData: SendTransactionData,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val cautions: List<HSCaution> = listOf()
) : ISwapFinalQuote

internal fun stonFiMinimumAskUnits(
    direction: SwapAmountDirection,
    amount: BigDecimal,
    tokenOutDecimals: Int,
    simulatedMinimum: String,
): BigInteger = when (direction) {
    SwapAmountDirection.In -> BigInteger(simulatedMinimum)
    SwapAmountDirection.Out -> amount.movePointRight(tokenOutDecimals).toBigInteger()
}

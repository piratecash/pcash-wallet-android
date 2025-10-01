package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.StonFiGasParams
import cash.p.terminal.modules.multiswap.StonFiSwapData
import cash.p.terminal.modules.multiswap.SwapQuoteStonFi
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.settings.SwapSettingRecipient
import cash.p.terminal.modules.multiswap.settings.SwapSettingSlippage
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipient
import cash.p.terminal.modules.multiswap.ui.DataFieldSlippage
import cash.p.terminal.network.stonfi.domain.repository.StonFiRepository
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import com.tonapps.blockchain.ton.extensions.toByteArray
import io.horizontalsystems.core.entities.BlockchainType
import io.ktor.util.encodeBase64
import org.ton.bigint.BigInt
import org.ton.block.AddrStd
import timber.log.Timber
import java.math.BigDecimal

class StonFiProvider(
    private val stonFiRepository: StonFiRepository,
    private val walletUseCase: WalletUseCase,
) : IMultiSwapProvider {
    override val id = "stonfi"
    override val title = "STON.fi"
    override val icon = R.drawable.ic_ston_fi
    override val priority = 0

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
    ): ISwapQuote {
        val settingRecipient = SwapSettingRecipient(settings, tokenOut)
        val settingSlippage =
            SwapSettingSlippage(settings, SLIPPAGE)

        val offerAddress = getTokenAddress(tokenIn)
        val askAddress = getTokenAddress(tokenOut)
        val units = amountIn.movePointRight(tokenIn.decimals).toBigInteger().toString()
        val referralAddressTon = REF_ADDRESS_TON
        val referralFeeBps = referralAddressTon?.let { REF_FEE_BPS }

        val response = stonFiRepository.simulateSwap(
            offerAddress = offerAddress,
            askAddress = askAddress,
            units = units,
            slippageTolerance = settingSlippage.valueOrDefault(),
            referralFeeBps = referralFeeBps,
            referralAddress = referralAddressTon,
            poolAddress = null, // Let STON.fi choose the best pool
            dexV2 = true
        )

        val amountOut = BigDecimal(response.askUnits).movePointLeft(tokenOut.decimals)
        val priceImpact = BigDecimal(response.priceImpact)

        val swapData = StonFiSwapData(
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
                gasBudget = response.gasParams.gasBudget
            )
        )

        val fields = buildList {
            settingRecipient.value?.let { add(DataFieldRecipient(it)) }
            settingSlippage.value?.let { add(DataFieldSlippage(it)) }
        }

        return SwapQuoteStonFi(
            amountOut = amountOut,
            priceImpact = priceImpact,
            fields = fields,
            settings = listOf(settingRecipient, settingSlippage),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = getActionRequired(tokenIn, tokenOut),
            swapData = swapData
        )
    }

    private fun getActionRequired(
        tokenIn: Token,
        tokenOut: Token
    ): ActionCreate? {
        val tokenInWalletCreated = walletUseCase.getWallet(tokenIn) != null
        val tokenOutWalletCreated = walletUseCase.getWallet(tokenOut) != null


        return if (!tokenInWalletCreated || !tokenOutWalletCreated) {
            val tokensToAdd = mutableSetOf<Token>()
            if (!tokenInWalletCreated) {
                tokensToAdd.add(tokenIn)
            }
            if (!tokenOutWalletCreated) {
                tokensToAdd.add(tokenOut)
            }
            ActionCreate(
                inProgress = false,
                descriptionResId = R.string.swap_create_wallet_description,
                tokensToAdd = tokensToAdd
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
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote
    ): ISwapFinalQuote {
        check(swapQuote is SwapQuoteStonFi)

        val settingRecipient = SwapSettingRecipient(swapSettings, tokenOut)
        val settingSlippage = SwapSettingSlippage(swapSettings, SLIPPAGE)

        // Get fresh quote for final transaction
        val offerAddress = getTokenAddress(tokenIn)
        val askAddress = getTokenAddress(tokenOut)
        val units = amountIn.movePointRight(tokenIn.decimals).toBigInteger().toString()

        val response = stonFiRepository.simulateSwap(
            offerAddress = offerAddress,
            askAddress = askAddress,
            units = units,
            slippageTolerance = settingSlippage.valueOrDefault(),
            poolAddress = swapQuote.swapData.poolAddress,
            referralAddress = REF_ADDRESS_TON,
            referralFeeBps = REF_FEE_BPS
        )

        val amountOut = BigDecimal(response.askUnits).movePointLeft(tokenOut.decimals)
        val minAmountOut = BigDecimal(response.minAskUnits).movePointLeft(tokenOut.decimals)

        val fields = buildList {
            settingRecipient.value?.let { add(DataFieldRecipient(it)) }
            settingSlippage.value?.let { add(DataFieldSlippage(it)) }
        }

        val addressFrom = walletUseCase.getReceiveAddress(tokenIn)
        val walletAddressTo = walletUseCase.getReceiveAddress(tokenOut)
        val receiverOwnerAddress = settingRecipient.value?.hex ?: walletAddressTo
        val receiverAddress = receiverOwnerAddress

        val routerInfo = stonFiRepository.getRouter(swapQuote.swapData.routerAddress)

        val ptonWalletAddress = when {
            // jetton -> ...
            tokenIn.type is TokenType.Jetton -> runCatching {
                stonFiRepository.getJettonAddress(
                    (tokenIn.type as TokenType.Jetton).address,
                    receiverOwnerAddress
                )
            }.getOrNull()

            // ton -> ...
            else -> routerInfo.ptonWalletAddress
        }

        val forwardGasDecimal = response.gasParams.forwardGas ?: BigDecimal.ZERO
        val forwardGasBigInt = try {
            BigInt(forwardGasDecimal.toBigIntegerExact().toString())
        } catch (_: ArithmeticException) {
            BigInt(forwardGasDecimal.toBigInteger().toString())
        }

        val tonTransferQueryId = System.currentTimeMillis()

        val swapPayload = when {
            tokenIn.type is TokenType.Jetton ->
                buildJettonToTonPayload(
                    amount = amountIn.movePointRight(tokenIn.decimals).toBigInteger(),
                    router = AddrStd(response.routerAddress),
                    ptonWallet = AddrStd(response.askJettonWallet),
                    refundAddress = AddrStd(addressFrom),
                    minOut = BigInt(response.minAskUnits),
                    forwardGas = forwardGasBigInt,
                    queryId = tonTransferQueryId,
                    refFee = REF_FEE_BPS,
                    referralAddress = REF_ADDRESS_TON?.let { AddrStd(it) }
                )

            else -> {
                val offerAmountBigInt = try {
                    BigInt(response.offerUnits.toBigIntegerExact().toString())
                } catch (_: ArithmeticException) {
                    BigInt(response.offerUnits.toBigInteger().toString())
                }
                buildStonfiSwapTonToJettonPayload(
                    tonAmount = offerAmountBigInt,
                    tokenWallet = AddrStd(response.askJettonWallet),
                    refundAddress = AddrStd(addressFrom),
                    minOut = BigInt(response.minAskUnits),
                    receiver = AddrStd(receiverAddress),
                    refFee = REF_FEE_BPS,
                    fwdGas = forwardGasBigInt,
                    referralAddress = REF_ADDRESS_TON?.let { AddrStd(it) }
                )
            }
        }

        val gasBudget = response.gasParams.gasBudget?.let { BigDecimal(it) }

        val sendTransactionData = SendTransactionData.TonSwap(
            offerUnits = response.offerUnits,
            forwardGas = forwardGasDecimal,
            routerAddress = swapQuote.swapData.routerAddress,
            routerMasterAddress = routerInfo.ptonMasterAddress,
            ptonWalletAddress = ptonWalletAddress,
            queryId = tonTransferQueryId,
            slippage = settingSlippage.valueOrDefault(),
            payload = swapPayload.toByteArray().encodeBase64(),
            gasBudget = gasBudget
        )

        return SwapFinalQuoteTon(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = amountOut,
            amountOutMin = minAmountOut,
            sendTransactionData = sendTransactionData,
            priceImpact = BigDecimal(response.priceImpact),
            fields = fields
        )
    }

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

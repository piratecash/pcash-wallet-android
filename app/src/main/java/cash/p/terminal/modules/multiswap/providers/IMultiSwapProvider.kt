package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import java.math.BigDecimal

interface IMultiSwapProvider {
    val id: String
    val title: String
    val icon: Int

    val walletUseCase: WalletUseCase

    val mevProtectionAvailable: Boolean

    suspend fun start() = Unit

    suspend fun supports(tokenFrom: Token, tokenTo: Token): Boolean {
        return (tokenFrom.blockchainType == tokenTo.blockchainType) &&
            supports(tokenFrom)
    }

    suspend fun supports(token: Token): Boolean
    suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>
    ): ISwapQuote

    suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote
    ) : ISwapFinalQuote

    fun getCreateTokenActionRequired(tokens: List<Token>): ActionCreate? {
        val tokensToAdd = tokens.filterTo(mutableSetOf()) { token ->
            walletUseCase.getWallet(token) == null
        }

        return if (tokensToAdd.isNotEmpty()) {
            ActionCreate(
                inProgress = false,
                descriptionResId = R.string.swap_create_wallet_description,
                tokensToAdd = tokensToAdd
            )
        } else {
            null
        }
    }

    suspend fun getWarningMessage(tokenIn: Token, tokenOut: Token): TranslatableString? = null

    fun getProviderTransactionId(): String? = null

    fun onTransactionCompleted(result: SendTransactionResult) = Unit
}

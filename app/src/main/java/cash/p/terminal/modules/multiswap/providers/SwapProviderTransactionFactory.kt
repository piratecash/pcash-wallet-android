package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import java.math.BigDecimal

class SwapProviderTransactionFactory(
    private val walletUseCase: WalletUseCase,
    private val accountManager: IAccountManager,
) {
    /**
     * @param recipientAddressOut the actual destination the funds are sent to. When a custom
     * recipient is set it must be stored so status polling selects the correct outbound;
     * defaults to the wallet's own receive address for [tokenOut].
     */
    fun build(
        provider: SwapProvider,
        transactionId: String,
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        amountOut: BigDecimal,
        recipientAddressOut: String? = null,
    ) = SwapProviderTransaction(
        date = System.currentTimeMillis(),
        outgoingRecordUid = null,
        transactionId = transactionId,
        status = TransactionStatusEnum.NEW.name.lowercase(),
        provider = provider,
        coinUidIn = tokenIn.coin.uid,
        blockchainTypeIn = tokenIn.blockchainType.uid,
        amountIn = amountIn,
        addressIn = walletUseCase.getReceiveAddress(tokenIn),
        coinUidOut = tokenOut.coin.uid,
        blockchainTypeOut = tokenOut.blockchainType.uid,
        amountOut = amountOut,
        addressOut = recipientAddressOut ?: walletUseCase.getReceiveAddress(tokenOut),
        accountId = accountManager.activeAccount?.id.orEmpty(),
    )
}

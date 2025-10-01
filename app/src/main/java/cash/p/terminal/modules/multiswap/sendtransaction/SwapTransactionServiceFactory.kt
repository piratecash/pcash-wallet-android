package cash.p.terminal.modules.multiswap.sendtransaction

import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceTonSwap
import cash.p.terminal.wallet.Token

object SwapTransactionServiceFactory {
    fun create(token: Token, swapProvider: IMultiSwapProvider): ISendTransactionService<*> = try {
        when {
            swapProvider is StonFiProvider -> {
                SendTransactionServiceTonSwap(token)
            }

            else -> {
                SendTransactionServiceFactory.create(token)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw UnsupportedException(e.message ?: "")
    }
}

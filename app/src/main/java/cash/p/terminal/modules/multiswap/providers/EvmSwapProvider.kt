package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.modules.multiswap.action.ActionApprove
import cash.p.terminal.modules.multiswap.action.ActionRevoke
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.wallet.Token
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import kotlinx.coroutines.rx2.await
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import kotlin.getValue

abstract class EvmSwapProvider : IMultiSwapProvider {
    override val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)

    protected suspend fun getAllowance(token: Token, spenderAddress: Address): BigDecimal? {
        if (token.type !is TokenType.Eip20) return null

        val eip20Adapter = App.adapterManager.getAdapterForToken<Eip20Adapter>(token) ?: return null

        return eip20Adapter.allowance(spenderAddress, DefaultBlockParameter.Latest).await()
    }

    protected fun actionApprove(
        allowance: BigDecimal?,
        amountIn: BigDecimal,
        routerAddress: Address,
        token: Token,
    ): ISwapProviderAction? {
        if (allowance == null || allowance >= amountIn) return null
        val eip20Adapter = App.adapterManager.getAdapterForToken<Eip20Adapter>(token) ?: return null

        val approveTransaction = eip20Adapter.pendingTransactions
            .filterIsInstance<EvmTransactionRecord>()
            .filter {
                it.transactionRecordType == TransactionRecordType.EVM_APPROVE &&
                        it.spender.equals(routerAddress.eip55, true)
            }
            .maxByOrNull { it.timestamp }

        val revoke = allowance > BigDecimal.ZERO && isUsdt(token)

        return if (revoke) {
            val revokeInProgress = approveTransaction != null && approveTransaction.value!!.zeroValue
            ActionRevoke(
                token,
                routerAddress.eip55,
                revokeInProgress,
                allowance
            )
        } else {
            val approveInProgress = approveTransaction != null && !approveTransaction.value!!.zeroValue
            ActionApprove(
                amountIn,
                routerAddress.eip55,
                token,
                approveInProgress
            )
        }
    }

    private fun isUsdt(token: Token): Boolean {
        val tokenType = token.type

        return token.blockchainType is BlockchainType.Ethereum
            && tokenType is TokenType.Eip20
            && tokenType.address.lowercase() == "0xdac17f958d2ee523a2206206994597c13d831ec7"
    }

}

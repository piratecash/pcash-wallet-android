package cash.p.terminal.core.adapters

import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.core.managers.SolanaKitWrapper
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.models.FullTransaction
import java.math.BigDecimal

abstract class BaseSolanaAdapter(
        solanaKitWrapper: SolanaKitWrapper,
        val decimal: Int
) : IAdapter, IBalanceAdapter, IReceiveAdapter, ISendSolanaAdapter {

    val solanaKit = solanaKitWrapper.solanaKit
    protected val signer: Signer? = solanaKitWrapper.signer

    override val debugInfo: String
        get() = solanaKit.debugInfo()

    override val statusInfo: Map<String, Any>
        get() = solanaKit.statusInfo()

    // IReceiveAdapter

    override val receiveAddress: String
        get() = solanaKit.receiveAddress

    override val isMainNet: Boolean
        get() = solanaKit.isMainnet

    override val maxSpendableBalance: BigDecimal
        get() = super.maxSpendableBalance

    override fun estimateFee(rawTransaction: ByteArray): BigDecimal {
        return solanaKit.estimateFee(rawTransaction)
    }

    override suspend fun send(rawTransaction: ByteArray): FullTransaction {
        if (signer == null) throw Exception()

        return solanaKit.sendRawTransaction(rawTransaction, signer)
    }

    companion object {
        const val confirmationsThreshold: Int = 12
    }

}

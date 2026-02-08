package cash.p.terminal.modules.multiswap.sendtransaction

import cash.p.terminal.entities.CoinValue
import cash.p.terminal.strings.R
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.network.CreatedTransaction
import java.math.BigDecimal
import java.math.BigInteger

sealed class SendTransactionData {
    data class Evm(
        val transactionData: TransactionData,
        val gasLimit: Long?,
        val feesMap: Map<FeeType, CoinValue> = mapOf()
    ) : SendTransactionData()

    data class Btc(
        val address: String,
        val memo: String,
        val amount: BigDecimal,
        val recommendedGasRate: Int?,
        val minimumSendAmount: Int?,
        val changeToFirstInput: Boolean,
        val utxoFilters: UtxoFilters,
        val feesMap: Map<FeeType, CoinValue>
    ) : SendTransactionData()

    sealed class Tron : SendTransactionData() {
        data class Regular(
            val address: String,
            val amount: BigDecimal
        ) : Tron()

        data class WithContract(val contract: Contract) : Tron()
        data class WithCreateTransaction(val transaction: CreatedTransaction) : Tron()
    }

    sealed class Solana : SendTransactionData() {
        data class Regular(
            val address: String,
            val amount: BigDecimal
        ) : Solana()

        data class WithRawTransaction(
            val rawTransactionStr: String,
            val rawTransactionAddress: String,
            val rawTransactionAmount: BigDecimal
        ) : Solana()
    }

    sealed class Stellar : SendTransactionData() {
        data class Regular(
            val address: String,
            val memo: String,
            val amount: BigDecimal
        ) : Stellar()

        data class WithTransactionEnvelope(val transactionEnvelope: String) : Stellar()
    }

    class Ton(
        val address: String,
        val amount: BigDecimal,
        val memo: String?
    ) : SendTransactionData()

    data class TonSwap(
        val forwardGas: BigInteger,
        val offerUnits: BigInteger,
        val routerAddress: String,
        val routerMasterAddress: String,
        val destinationAddress: String?,
        val queryId: Long?,
        val slippage: BigDecimal,
        val payload: String,
        val gasBudget: BigInteger? = null
    ) : SendTransactionData()

    class Monero(
        val address: String,
        val amount: BigDecimal
    ) : SendTransactionData()

    object Unsupported : SendTransactionData()
}

enum class FeeType(val stringResId: Int) {
    Outbound(R.string.Fee_OutboundFee),
    Liquidity(R.string.Fee_LiquidityFee);
}

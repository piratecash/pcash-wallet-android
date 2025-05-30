package cash.p.terminal.entities.transactionrecords.evm

import cash.p.terminal.core.adapters.BaseEvmAdapter
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.ethereumkit.models.Transaction
import java.math.BigDecimal

class EvmTransactionRecord(
    spamManager: SpamManager? = null,
    from: String? = null,
    to: String? = null,
    sentToSelf: Boolean = false,
    transaction: Transaction,
    token: Token,
    source: TransactionSource,
    spam: Boolean = false,
    transactionRecordType: TransactionRecordType,
    val spender: String? = null,
    val value: TransactionValue? = null,
    val contractAddress: String? = null,
    val method: String? = null,
    val incomingEvents: List<TransferEvent>? = null,
    val outgoingEvents: List<TransferEvent>? = null,
    val exchangeAddress: String? = null,
    val amountIn: Amount? = null,
    val amountOut: Amount? = null,
    val valueIn: TransactionValue? = amountIn?.value,
    val valueOut: TransactionValue? = amountOut?.value,
    val recipient: String? = null,
    val foreignTransaction: Boolean = false,
) : TransactionRecord(
    uid = transaction.hashString,
    transactionHash = transaction.hashString,
    transactionIndex = transaction.transactionIndex ?: 0,
    blockHeight = transaction.blockNumber?.toInt(),
    confirmationsThreshold = BaseEvmAdapter.confirmationsThreshold,
    timestamp = transaction.timestamp,
    failed = transaction.isFailed,
    spam = spam,
    source = source,
    transactionRecordType = transactionRecordType,
    token = token,
    to = to,
    from = from,
    sentToSelf = sentToSelf
) {

    sealed class Amount(val value: TransactionValue) {
        class Exact(value: TransactionValue) : Amount(value)
        class Extremum(value: TransactionValue) : Amount(value)
    }

    override val mainValue: TransactionValue?
        get() {
            return if (transactionRecordType == TransactionRecordType.EVM_CONTRACT_CALL ||
                transactionRecordType == TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL
            ) {
                val (incomingValues, outgoingValues) = combined(incomingEvents!!, outgoingEvents!!)

                when {
                    (incomingValues.isEmpty() && outgoingValues.size == 1) -> outgoingValues.first()
                    (incomingValues.size == 1 && outgoingValues.isEmpty()) -> incomingValues.first()
                    else -> null
                }
            } else {
                value
            }
        }

    val fee: TransactionValue?

    init {
        val feeAmount: Long? = transaction.gasUsed ?: transaction.gasLimit
        val gasPrice = transaction.gasPrice

        fee = if (feeAmount != null && gasPrice != null) {
            val feeDecimal = feeAmount.toBigDecimal()
                .multiply(gasPrice.toBigDecimal())
                .movePointLeft(token.decimals).stripTrailingZeros()

            TransactionValue.CoinValue(token, feeDecimal)
        } else {
            null
        }
    }

    companion object {
        private fun sameType(value: TransactionValue, value2: TransactionValue): Boolean =
            when {
                value is TransactionValue.CoinValue && value2 is TransactionValue.CoinValue ->
                    value.token == value2.token

                value is TransactionValue.TokenValue && value2 is TransactionValue.TokenValue ->
                    value.tokenName == value2.tokenName && value.tokenCode == value2.tokenCode && value.tokenDecimals == value2.tokenDecimals

                value is TransactionValue.NftValue && value2 is TransactionValue.NftValue ->
                    value.nftUid == value2.nftUid

                else ->
                    false
            }

        fun combined(
            incomingEvents: List<TransferEvent>,
            outgoingEvents: List<TransferEvent>
        ): Pair<List<TransactionValue>, List<TransactionValue>> {
            val values = (incomingEvents + outgoingEvents).map { it.value }
            val resultIncoming: MutableList<TransactionValue> = mutableListOf()
            val resultOutgoing: MutableList<TransactionValue> = mutableListOf()

            for (value in values) {
                if ((resultIncoming + resultOutgoing).any { sameType(value, it) }) {
                    continue
                }

                val sameTypeValues = values.filter { sameType(value, it) }
                val totalValue = sameTypeValues.map { it.decimalValue ?: BigDecimal.ZERO }
                    .reduce { sum, t -> sum + t }
                val resultValue = when (value) {
                    is TransactionValue.CoinValue -> TransactionValue.CoinValue(
                        value.token,
                        totalValue
                    )

                    is TransactionValue.TokenValue -> TransactionValue.TokenValue(
                        tokenName = value.tokenName,
                        tokenCode = value.tokenCode,
                        tokenDecimals = value.tokenDecimals,
                        value = totalValue,
                        coinIconPlaceholder = value.coinIconPlaceholder
                    )

                    is TransactionValue.RawValue -> value
                    is TransactionValue.NftValue -> value.copy(value = totalValue)
                    is TransactionValue.JettonValue -> value
                }

                if (totalValue > BigDecimal.ZERO) {
                    resultIncoming.add(resultValue)
                } else {
                    resultOutgoing.add(resultValue)
                }
            }

            return Pair(resultIncoming, resultOutgoing)
        }
    }

}

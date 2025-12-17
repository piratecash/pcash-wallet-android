package cash.p.terminal.core.storage

import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class SwapProviderTransactionsStorage(private val dao: SwapProviderTransactionsDao) {

    private companion object Companion {
        const val THRESHOLD_MSEC = 40_000
        const val AMOUNT_TOLERANCE = 0.005 // 0.5%
        const val FINISHED_AT_WINDOW_MS = 1_800_000L // ±30 minutes
        const val FALLBACK_WINDOW_MS = 21_600_000L // ±6 hours
    }

    fun save(
        swapProviderTransaction: SwapProviderTransaction
    ) = dao.insert(swapProviderTransaction)

    fun getAll(
        token: Token,
        address: String,
        statusesExcluded: List<String>,
        limit: Int
    ) = dao.getAll(
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        address = address,
        statusesExcluded = statusesExcluded,
        limit = limit
    )

    fun getTransaction(transactionId: String) = dao.getTransaction(transactionId)

    fun getByCoinUidIn(
        coinUid: String,
        blockchainType: String,
        amountIn: BigDecimal?,
        timestamp: Long
    ) = dao.getByTokenIn(
        coinUid = coinUid,
        amountIn = amountIn,
        blockchainType = blockchainType,
        dateFrom = timestamp - THRESHOLD_MSEC,
        dateTo = timestamp + THRESHOLD_MSEC
    )

    fun getByOutgoingRecordUid(
        outgoingRecordUid: String
    ) = dao.getByOutgoingRecordUid(
        outgoingRecordUid = outgoingRecordUid
    )

    fun getByTokenOut(
        token: Token,
        timestamp: Long
    ) = dao.getByTokenOut(
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        dateFrom = timestamp - THRESHOLD_MSEC,
        dateTo = timestamp + THRESHOLD_MSEC
    )

    fun getByAddressAndAmount(
        address: String,
        blockchainType: String,
        coinUid: String,
        amount: BigDecimal,
        timestamp: Long
    ): SwapProviderTransaction? = dao.getByAddressAndAmount(
        address = address,
        blockchainType = blockchainType,
        coinUid = coinUid,
        amount = amount.toDouble(),
        tolerance = AMOUNT_TOLERANCE,
        timestamp = timestamp,
        timeWindowMs = FINISHED_AT_WINDOW_MS,
        dateFrom = timestamp - FALLBACK_WINDOW_MS,
        dateTo = timestamp + FALLBACK_WINDOW_MS
    )

    fun setIncomingRecordUid(date: Long, incomingRecordUid: String) =
        dao.setIncomingRecordUid(date, incomingRecordUid)

    fun updateStatusFields(
        transactionId: String,
        status: String,
        amountOutReal: BigDecimal?,
        finishedAt: Long?
    ) = dao.updateStatusFields(transactionId, status, amountOutReal, finishedAt)
}

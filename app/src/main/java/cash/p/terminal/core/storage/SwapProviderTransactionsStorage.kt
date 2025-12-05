package cash.p.terminal.core.storage

import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class SwapProviderTransactionsStorage(private val dao: SwapProviderTransactionsDao) {

    private companion object Companion {
        const val THRESHOLD_MSEC = 40_000
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
}

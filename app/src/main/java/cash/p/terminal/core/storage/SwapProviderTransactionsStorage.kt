package cash.p.terminal.core.storage

import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class SwapProviderTransactionsStorage(
    private val dao: SwapProviderTransactionsDao,
    private val dispatcherProvider: DispatcherProvider
) {

    private companion object Companion {
        const val THRESHOLD_MSEC = 40_000
        const val AMOUNT_TOLERANCE = 0.005 // 0.5%
        const val FINISHED_AT_WINDOW_MS = 1_800_000L // ±30 minutes
        const val LEGACY_WINDOW_BEFORE_MS = 3_600_000L  // 1 hour before
        const val LEGACY_WINDOW_AFTER_MS = 10_800_000L  // 3 hours after
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

    fun getAllUnfinishedByAccount(
        accountId: String,
        statusesExcluded: List<String>,
        limit: Int
    ) = dao.getAllUnfinishedByAccount(
        accountId = accountId,
        statusesExcluded = statusesExcluded,
        limit = limit
    )

    fun observeByToken(
        token: Token,
        address: String,
        limit: Int = 50
    ): Flow<List<SwapProviderTransaction>> = dao.observeByToken(
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        address = address,
        limit = limit
    )

    fun observeAll(): Flow<List<SwapProviderTransaction>> = dao.observeAll()

    fun observeAllByAccount(
        accountId: String,
        limit: Int = 100
    ): Flow<List<SwapProviderTransaction>> = dao.observeAllByAccount(accountId, limit)

    fun observeForActiveAccount(
        activeAccountStateFlow: Flow<ActiveAccountState>
    ): Flow<List<SwapProviderTransaction>> =
        activeAccountStateFlow.flatMapLatest { state ->
            val accountId = (state as? ActiveAccountState.ActiveAccount)?.account?.id
            if (accountId != null) observeAllByAccount(accountId) else flowOf(emptyList())
        }

    fun observeByDate(date: Long): Flow<SwapProviderTransaction?> = dao.observeByDate(date)

    suspend fun getTransaction(transactionId: String) =
        withContext(dispatcherProvider.io) {
            dao.getTransaction(transactionId)
        }

    suspend fun getByDate(date: Long): SwapProviderTransaction? =
        withContext(dispatcherProvider.io) {
            dao.getByDate(date)
        }

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

    fun getByIncomingRecordUid(
        incomingRecordUid: String
    ) = dao.getByIncomingRecordUid(
        incomingRecordUid = incomingRecordUid
    )

    fun getByTokenOut(
        coinUid: String,
        blockchainType: String,
        timestamp: Long,
        accountId: String
    ) = dao.getByTokenOut(
        coinUid = coinUid,
        blockchainType = blockchainType,
        accountId = accountId,
        dateFrom = timestamp - THRESHOLD_MSEC,
        dateTo = timestamp + THRESHOLD_MSEC
    )

    fun getByAddressAndAmount(
        address: String,
        blockchainType: String,
        coinUid: String,
        accountId: String,
        amount: BigDecimal,
        timestamp: Long
    ): SwapProviderTransaction? = dao.getByAddressAndAmount(
        address = address,
        blockchainType = blockchainType,
        coinUid = coinUid,
        accountId = accountId,
        amount = amount.toDouble(),
        tolerance = AMOUNT_TOLERANCE,
        timestamp = timestamp,
        timeWindowMs = FINISHED_AT_WINDOW_MS,
        dateFrom = timestamp - SwapTransactionMatcher.TIME_WINDOW_MS,
        dateTo = timestamp + SwapTransactionMatcher.TIME_WINDOW_MS
    )

    fun setIncomingRecordUid(date: Long, incomingRecordUid: String, amountOutReal: BigDecimal) =
        dao.setIncomingRecordUid(date, incomingRecordUid, amountOutReal)

    fun setOutgoingRecordUid(date: Long, outgoingRecordUid: String) =
        dao.setOutgoingRecordUid(date, outgoingRecordUid)

    fun updateStatusFields(
        date: Long,
        status: String,
        amountOutReal: BigDecimal?,
        finishedAt: Long?
    ) = dao.updateStatusFields(date, status, amountOutReal, finishedAt)

    fun updateTransactionId(date: Long, newTransactionId: String) =
        dao.updateTransactionId(date, newTransactionId)

    fun getByProviderAndTokenOut(
        provider: SwapProvider,
        coinUidOut: String,
        blockchainTypeOut: String,
        accountId: String,
        addressOut: String,
        expectedAmount: BigDecimal,
        legStartTime: Long,
    ): SwapProviderTransaction? = dao.getByProviderAndTokenOut(
        provider = provider.name,
        coinUidOut = coinUidOut,
        blockchainTypeOut = blockchainTypeOut,
        accountId = accountId,
        addressOut = addressOut,
        expectedAmount = expectedAmount.toDouble(),
        tolerance = AMOUNT_TOLERANCE,
        dateFrom = legStartTime - LEGACY_WINDOW_BEFORE_MS,
        dateTo = legStartTime + LEGACY_WINDOW_AFTER_MS,
    )

    fun getUnmatchedSwapsByTokenOut(
        coinUid: String,
        blockchainType: String,
        fromTimestamp: Long,
        toTimestamp: Long,
        amount: BigDecimal,
        tolerance: Double,
        accountId: String,
        limit: Int = 100
    ): List<SwapProviderTransaction> = dao.getUnmatchedSwapsByTokenOut(
        coinUid = coinUid,
        blockchainType = blockchainType,
        accountId = accountId,
        dateFrom = fromTimestamp,
        dateTo = toTimestamp,
        amount = amount.toDouble(),
        tolerance = tolerance,
        limit = limit
    )
}

fun List<SwapProviderTransaction>.toRecordUidMap(): Map<String, SwapProviderTransaction> {
    return flatMap { swap ->
        listOfNotNull(
            swap.incomingRecordUid?.let { it to swap },
            swap.outgoingRecordUid?.let { it to swap }
        )
    }.toMap()
}

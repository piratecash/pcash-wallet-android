package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.SwapProviderTransaction
import java.math.BigDecimal

@Dao
interface SwapProviderTransactionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(swapProviderTransaction: SwapProviderTransaction)

    @Query(
        "SELECT * FROM SwapProviderTransaction WHERE " +
                "((coinUidIn = :coinUid AND blockchainTypeIn = :blockchainType AND addressIn = :address) OR " +
                "(coinUidOut = :coinUid AND blockchainTypeOut = :blockchainType AND addressOut = :address)) AND " +
                "status not in (:statusesExcluded) ORDER BY date DESC LIMIT :limit"
    )
    fun getAll(
        coinUid: String,
        blockchainType: String,
        address: String,
        statusesExcluded: List<String>,
        limit: Int
    ): List<SwapProviderTransaction>

    @Query("SELECT * FROM SwapProviderTransaction WHERE transactionId = :transactionId")
    fun getTransaction(transactionId: String): SwapProviderTransaction?

    @Query(
        "SELECT * FROM SwapProviderTransaction WHERE " +
                "(coinUidIn = :coinUid AND blockchainTypeIn = :blockchainType AND date >= :dateFrom AND date <= :dateTo) " +
                "AND (:amountIn is NULL OR amountIn == :amountIn) ORDER BY date DESC LIMIT 1"
    )
    fun getByTokenIn(
        coinUid: String,
        amountIn: BigDecimal?,
        blockchainType: String,
        dateFrom: Long,
        dateTo: Long
    ): SwapProviderTransaction?

    @Query("SELECT * FROM SwapProviderTransaction WHERE outgoingRecordUid = :outgoingRecordUid")
    fun getByOutgoingRecordUid(
        outgoingRecordUid: String
    ): SwapProviderTransaction?

    @Query("SELECT * FROM SwapProviderTransaction WHERE incomingRecordUid = :incomingRecordUid")
    fun getByIncomingRecordUid(
        incomingRecordUid: String
    ): SwapProviderTransaction?

    @Query(
        "SELECT * FROM SwapProviderTransaction WHERE " +
                "(coinUidOut = :coinUid AND blockchainTypeOut = :blockchainType AND date >= :dateFrom AND date <= :dateTo) ORDER BY date DESC LIMIT 1"
    )
    fun getByTokenOut(
        coinUid: String,
        blockchainType: String,
        dateFrom: Long,
        dateTo: Long
    ): SwapProviderTransaction?

    @Query(
        """
        SELECT * FROM SwapProviderTransaction WHERE
        addressOut = :address
        AND blockchainTypeOut = :blockchainType
        AND coinUidOut = :coinUid
        AND incomingRecordUid IS NULL
        AND amountOutReal IS NOT NULL
        AND CAST(amountOutReal AS REAL) != 0
        AND ABS(CAST(amountOutReal AS REAL) - :amount) / CAST(amountOutReal AS REAL) < :tolerance
        AND (
            (finishedAt IS NOT NULL AND :timestamp >= finishedAt - :timeWindowMs AND :timestamp <= finishedAt + :timeWindowMs)
            OR
            (finishedAt IS NULL AND date >= :dateFrom AND date <= :dateTo)
        )
        ORDER BY ABS(CAST(amountOutReal AS REAL) - :amount) ASC, date ASC
        LIMIT 1
        """
    )
    fun getByAddressAndAmount(
        address: String,
        blockchainType: String,
        coinUid: String,
        amount: Double,
        tolerance: Double,
        timestamp: Long,
        timeWindowMs: Long,
        dateFrom: Long,
        dateTo: Long
    ): SwapProviderTransaction?

    @Query("UPDATE SwapProviderTransaction SET incomingRecordUid = :incomingRecordUid, amountOutReal = :amountOutReal WHERE date = :date")
    fun setIncomingRecordUid(date: Long, incomingRecordUid: String, amountOutReal: BigDecimal)

    @Query("UPDATE SwapProviderTransaction SET status = :status, amountOutReal = :amountOutReal, finishedAt = :finishedAt WHERE transactionId = :transactionId")
    fun updateStatusFields(transactionId: String, status: String, amountOutReal: BigDecimal?, finishedAt: Long?)
}

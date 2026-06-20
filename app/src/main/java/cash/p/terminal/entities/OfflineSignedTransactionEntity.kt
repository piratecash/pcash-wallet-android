package cash.p.terminal.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import cash.p.terminal.wallet.entities.AccountRecord

@Entity(
    tableName = "OfflineSignedTransaction",
    primaryKeys = ["accountId", "txHash"],
    indices = [
        Index(value = ["accountId", "createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class OfflineSignedTransactionEntity(
    val accountId: String,
    val txHash: String,
    val blockchainTypeUid: String,
    val coinCode: String,
    val tokenDecimals: Int,
    val amount: String,
    val toAddress: String,
    val rawHex: String,
    val pcashPayload: String,
    val createdAt: Long,
    val status: String,
    val broadcastAttempts: Int,
    val lastBroadcastAt: Long?,
    val broadcastedAt: Long?,
    val lastError: String?,
)

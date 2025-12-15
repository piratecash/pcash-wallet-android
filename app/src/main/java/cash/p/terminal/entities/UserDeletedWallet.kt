package cash.p.terminal.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import cash.p.terminal.wallet.entities.AccountRecord

@Entity(
    primaryKeys = ["accountId", "tokenQueryId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [Index("accountId", name = "index_UserDeletedWallet_accountId")]
)
data class UserDeletedWallet(
    val accountId: String,
    val tokenQueryId: String
)

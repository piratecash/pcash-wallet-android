package cash.p.terminal.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.SecretString

/**
 * Represents a record of a Monero file associated with Mnemonic account
 * if it has Monero token
 */
@Entity(
    primaryKeys = ["accountId"],
    foreignKeys = [ForeignKey(
        entity = AccountRecord::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
        deferred = true
    )
    ],
    indices = [Index(value = ["accountId"])]
)
data class MoneroFileRecord(
    val accountId: String,
    val fileName: SecretString,
    val password: SecretString
)

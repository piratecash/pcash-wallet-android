package cash.p.terminal.wallet.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(primaryKeys = ["tokenQueryId", "accountId"],
        foreignKeys = [ForeignKey(
                entity = AccountRecord::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onUpdate = ForeignKey.CASCADE,
                onDelete = ForeignKey.CASCADE,
                deferred = true)
        ],
        indices = [Index("accountId", name = "index_EnabledWallet_accountId")])

data class EnabledWallet(
        val tokenQueryId: String,
        val accountId: String,
        val walletOrder: Int? = null,
        val coinName: String? = null,
        val coinCode: String? = null,
        val coinDecimals: Int? = null,
        val coinImage: String?
)

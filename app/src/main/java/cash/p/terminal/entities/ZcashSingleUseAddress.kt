package cash.p.terminal.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["accountId", "address"],
    indices = [
        Index(
            value = ["accountId", "hadBalance", "useCount"]
        )
    ]
)
data class ZcashSingleUseAddress(
    val accountId: String,
    val address: String,
    val useCount: Int,
    val hadBalance: Boolean
)

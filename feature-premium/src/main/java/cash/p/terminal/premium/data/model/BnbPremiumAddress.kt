package cash.p.terminal.premium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bnb_premium_address")
internal data class BnbPremiumAddress(
    @PrimaryKey
    val accountId: String,
    val address: String
) 
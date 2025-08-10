package cash.p.terminal.premium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "demo_premium_users")
internal data class DemoPremiumUser(
    @PrimaryKey
    val address: String,
    val lastCheckDate: Long,
    val daysLeft: Int
) 
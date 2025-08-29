package cash.p.terminal.premium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import cash.p.terminal.premium.domain.usecase.PremiumType

@Entity(tableName = "premium_users")
internal data class PremiumUser(
    @PrimaryKey
    val level: Int,
    val accountId: String,
    val address: String,
    val lastCheckDate: Long,
    val coinType: String,
    val isPremium: PremiumType
) 
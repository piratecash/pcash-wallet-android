package cash.p.terminal.premium.data.database

import androidx.room.TypeConverter
import cash.p.terminal.premium.domain.usecase.PremiumType

internal class PremiumTypeConverter {
    @TypeConverter
    fun fromPremiumType(premiumType: PremiumType): String {
        return premiumType.name
    }

    @TypeConverter
    fun toPremiumType(premiumType: String): PremiumType {
        return PremiumType.valueOf(premiumType)
    }
}
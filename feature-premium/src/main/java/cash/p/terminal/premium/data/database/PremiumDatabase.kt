package cash.p.terminal.premium.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.BnbPremiumAddressDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.database.migrations.Migration_5_6
import cash.p.terminal.premium.data.database.migrations.Migration_6_7
import cash.p.terminal.premium.data.database.migrations.Migration_7_8
import cash.p.terminal.premium.data.database.migrations.Migration_8_9
import cash.p.terminal.premium.data.model.AccountPremiumCacheEntity
import cash.p.terminal.premium.data.model.BnbPremiumAddress
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.premium.data.model.PremiumUser

@Database(
    entities = [
        PremiumUser::class,
        DemoPremiumUser::class,
        BnbPremiumAddress::class,
        AccountPremiumCacheEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(PremiumTypeConverter::class)
internal abstract class PremiumDatabase : RoomDatabase() {

    abstract fun premiumUserDao(): PremiumUserDao
    abstract fun demoPremiumUserDao(): DemoPremiumUserDao
    abstract fun bnbPremiumAddressDao(): BnbPremiumAddressDao
    abstract fun accountPremiumCacheDao(): AccountPremiumCacheDao

    companion object {
        private const val DATABASE_NAME = "premium_database"

        fun create(context: Context): PremiumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PremiumDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    Migration_5_6,
                    Migration_6_7,
                    Migration_7_8,
                    Migration_8_9,
                )
                .build()
        }
    }
}

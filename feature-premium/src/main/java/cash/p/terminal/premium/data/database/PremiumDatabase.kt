package cash.p.terminal.premium.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cash.p.terminal.premium.data.dao.BnbPremiumAddressDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.database.migrations.Migration_5_6
import cash.p.terminal.premium.data.model.BnbPremiumAddress
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.premium.data.model.PremiumUser

@Database(
    entities = [
        PremiumUser::class,
        DemoPremiumUser::class,
        BnbPremiumAddress::class
    ],
    version = 6,
    exportSchema = false
)
internal abstract class PremiumDatabase : RoomDatabase() {

    abstract fun premiumUserDao(): PremiumUserDao
    abstract fun demoPremiumUserDao(): DemoPremiumUserDao
    abstract fun bnbPremiumAddressDao(): BnbPremiumAddressDao

    companion object {
        private const val DATABASE_NAME = "premium_database"

        fun create(context: Context): PremiumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PremiumDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    Migration_5_6
                )
                .build()
        }
    }
}

package cash.p.terminal.premium.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.premium.data.model.PremiumUser

@Database(
    entities = [
        PremiumUser::class,
        DemoPremiumUser::class
    ],
    version = 5,
    exportSchema = false
)
internal abstract class PremiumDatabase : RoomDatabase() {

    abstract fun premiumUserDao(): PremiumUserDao
    abstract fun demoPremiumUserDao(): DemoPremiumUserDao

    companion object {
        private const val DATABASE_NAME = "premium_database"

        fun create(context: Context): PremiumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PremiumDatabase::class.java,
                DATABASE_NAME
            )
                .build()
        }
    }
}

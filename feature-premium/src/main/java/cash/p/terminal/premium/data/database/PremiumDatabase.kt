package cash.p.terminal.premium.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.model.PremiumUser

@Database(
    entities = [PremiumUser::class],
    version = 2,
    exportSchema = false
)
internal abstract class PremiumDatabase : RoomDatabase() {

    abstract fun premiumUserDao(): PremiumUserDao

    companion object {
        private const val DATABASE_NAME = "premium_database"

        fun create(context: android.content.Context): PremiumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PremiumDatabase::class.java,
                DATABASE_NAME
            )
                .build()
        }
    }
} 
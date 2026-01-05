package cash.p.terminal.feature.logging.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cash.p.terminal.feature.logging.data.dao.LoginRecordDao
import cash.p.terminal.feature.logging.data.model.LoginRecord

@Database(
    entities = [LoginRecord::class],
    version = 1,
    exportSchema = false
)
internal abstract class LoggingDatabase : RoomDatabase() {
    abstract fun loginRecordDao(): LoginRecordDao

    companion object {
        private const val DATABASE_NAME = "logging_database"

        fun create(context: Context): LoggingDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LoggingDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}

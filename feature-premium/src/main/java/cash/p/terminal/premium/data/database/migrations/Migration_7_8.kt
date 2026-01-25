package cash.p.terminal.premium.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_7_8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `demo_premium_users` (
                `address` TEXT PRIMARY KEY NOT NULL,
                `lastCheckDate` INTEGER NOT NULL,
                `daysLeft` INTEGER NOT NULL
            )
        """)
    }
}

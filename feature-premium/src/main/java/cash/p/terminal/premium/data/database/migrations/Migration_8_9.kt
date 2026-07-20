package cash.p.terminal.premium.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_8_9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `account_premium_cache` (
                `accountId` TEXT PRIMARY KEY NOT NULL,
                `premiumType` TEXT NOT NULL,
                `checkedAtEpochMillis` INTEGER NOT NULL
            )
        """)
    }
}

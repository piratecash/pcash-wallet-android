package cash.p.terminal.premium.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_5_6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bnb_premium_address` (
                `accountId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                PRIMARY KEY(`accountId`)
            )
        """)
    }
}

package cash.p.terminal.premium.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_6_7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE premium_users_temp (
                level INTEGER PRIMARY KEY NOT NULL,
                accountId TEXT NOT NULL,
                address TEXT NOT NULL,
                lastCheckDate INTEGER NOT NULL,
                coinType TEXT NOT NULL,
                isPremium TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO premium_users_temp (level, accountId, address, lastCheckDate, coinType, isPremium)
            SELECT level, accountId, address, lastCheckDate, coinType,
                   CASE
                       WHEN isPremium = 1 THEN 'COSA'
                       ELSE 'NONE'
                   END
            FROM premium_users
        """.trimIndent())

        db.execSQL("DROP TABLE premium_users")

        db.execSQL("ALTER TABLE premium_users_temp RENAME TO premium_users")
    }
}

package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_75_76 : Migration(75, 76) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `MoneroFileRecord` (
                accountId TEXT NOT NULL PRIMARY KEY,
                fileName TEXT NOT NULL,
                password TEXT NOT NULL,
                FOREIGN KEY(accountId) REFERENCES AccountRecord(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_MoneroFileRecord_accountId ON MoneroFileRecord(accountId)"
        )
    }
}

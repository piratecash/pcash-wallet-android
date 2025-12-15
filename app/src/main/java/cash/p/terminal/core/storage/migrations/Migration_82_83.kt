package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_82_83 : Migration(82, 83) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS UserDeletedWallet (
                accountId TEXT NOT NULL,
                tokenQueryId TEXT NOT NULL,
                PRIMARY KEY(accountId, tokenQueryId),
                FOREIGN KEY(accountId) REFERENCES AccountRecord(id) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_UserDeletedWallet_accountId ON UserDeletedWallet(accountId)")
    }
}

package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_86_87 : Migration(86, 87) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS LoginRecord (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                isSuccessful INTEGER NOT NULL,
                userLevel INTEGER NOT NULL,
                accountId TEXT NOT NULL,
                photoPath TEXT
            )
            """
        )
        db.execSQL("CREATE INDEX index_LoginRecord_accountId ON LoginRecord(accountId)")
        db.execSQL("CREATE INDEX index_LoginRecord_timestamp ON LoginRecord(timestamp)")
        db.execSQL("CREATE INDEX index_LoginRecord_userLevel ON LoginRecord(userLevel)")
    }
}

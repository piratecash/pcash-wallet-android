package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_80_81 : Migration(80, 81) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ZcashSingleUseAddress (
                accountId TEXT NOT NULL,
                address TEXT NOT NULL,
                useCount INTEGER NOT NULL,
                hadBalance INTEGER NOT NULL,
                PRIMARY KEY(accountId, address)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX index_ZcashSingleUseAddress_accountId_hadBalance_useCount
            ON ZcashSingleUseAddress(accountId, hadBalance, useCount)
            """.trimIndent()
        )
    }
}

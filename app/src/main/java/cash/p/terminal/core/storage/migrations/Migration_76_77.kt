package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_76_77 : Migration(76, 77) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE SpamScanState")

        db.execSQL(
            """
            CREATE TABLE SpamScanState (
                blockchainType TEXT NOT NULL,
                accountId TEXT NOT NULL,
                lastSyncedTransactionId TEXT NOT NULL,
                PRIMARY KEY(blockchainType, accountId)
            )
        """
        )
    }
}

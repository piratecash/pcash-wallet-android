package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_84_85 : Migration(84, 85) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table without payoutHash, with new columns
        db.execSQL(
            """
            CREATE TABLE SwapProviderTransaction_new (
                date INTEGER PRIMARY KEY NOT NULL,
                outgoingRecordUid TEXT,
                transactionId TEXT NOT NULL,
                status TEXT NOT NULL,
                provider TEXT NOT NULL,
                coinUidIn TEXT NOT NULL,
                blockchainTypeIn TEXT NOT NULL,
                amountIn TEXT NOT NULL,
                addressIn TEXT NOT NULL,
                coinUidOut TEXT NOT NULL,
                blockchainTypeOut TEXT NOT NULL,
                amountOut TEXT NOT NULL,
                addressOut TEXT NOT NULL,
                amountOutReal TEXT,
                finishedAt INTEGER,
                incomingRecordUid TEXT
            )
            """
        )

        // Copy data from old table (excluding payoutHash)
        db.execSQL(
            """
            INSERT INTO SwapProviderTransaction_new (
                date, outgoingRecordUid, transactionId, status, provider,
                coinUidIn, blockchainTypeIn, amountIn, addressIn,
                coinUidOut, blockchainTypeOut, amountOut, addressOut
            )
            SELECT
                date, outgoingRecordUid, transactionId, status, provider,
                coinUidIn, blockchainTypeIn, amountIn, addressIn,
                coinUidOut, blockchainTypeOut, amountOut, addressOut
            FROM SwapProviderTransaction
            """
        )

        // Drop old table
        db.execSQL("DROP TABLE SwapProviderTransaction")

        // Rename new table
        db.execSQL("ALTER TABLE SwapProviderTransaction_new RENAME TO SwapProviderTransaction")
    }
}

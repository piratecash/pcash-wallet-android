package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_81_82 : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table with provider field
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS SwapProviderTransaction (
                date INTEGER NOT NULL,
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
                PRIMARY KEY(date)
            )
            """.trimIndent()
        )

        // Copy data from old table to new table, setting provider to CHANGENOW for all existing records
        db.execSQL(
            """
            INSERT INTO SwapProviderTransaction
            (date, outgoingRecordUid, transactionId, status, provider,
             coinUidIn, blockchainTypeIn, amountIn, addressIn,
             coinUidOut, blockchainTypeOut, amountOut, addressOut)
            SELECT
                date, outgoingRecordUid, transactionId, status, 'CHANGENOW',
                coinUidIn, blockchainTypeIn, amountIn, addressIn,
                coinUidOut, blockchainTypeOut, amountOut, addressOut
            FROM ChangeNowTransaction
            """.trimIndent()
        )

        // Drop old table
        db.execSQL("DROP TABLE IF EXISTS ChangeNowTransaction")
    }
}

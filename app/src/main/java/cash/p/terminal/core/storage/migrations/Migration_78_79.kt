package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_78_79 : Migration(78, 79) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite doesn't support DROP COLUMN directly, so we need to:
        // 1. Create new table without utxoInputsJson
        // 2. Copy data from old table
        // 3. Drop old table
        // 4. Rename new table to original name

        db.execSQL(
            """
            CREATE TABLE PendingTransaction_new (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                coinUid TEXT NOT NULL,
                blockchainTypeUid TEXT NOT NULL,
                tokenTypeId TEXT,
                amountAtomic TEXT NOT NULL,
                feeAtomic TEXT,
                fromAddress TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                txHash TEXT,
                nonce INTEGER,
                memo TEXT,
                createdAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL
            )
        """
        )

        db.execSQL(
            """
            INSERT INTO PendingTransaction_new (
                id, walletId, coinUid, blockchainTypeUid, tokenTypeId,
                amountAtomic, feeAtomic, fromAddress, toAddress,
                txHash, nonce, memo, createdAt, expiresAt
            )
            SELECT
                id, walletId, coinUid, blockchainTypeUid, tokenTypeId,
                amountAtomic, feeAtomic, fromAddress, toAddress,
                txHash, nonce, memo, createdAt, expiresAt
            FROM PendingTransaction
        """
        )

        db.execSQL("DROP TABLE PendingTransaction")

        db.execSQL("ALTER TABLE PendingTransaction_new RENAME TO PendingTransaction")
    }
}

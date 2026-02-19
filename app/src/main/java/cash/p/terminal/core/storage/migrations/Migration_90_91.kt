package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_90_91 : Migration(90, 91) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE PendingTransaction_new (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                coinUid TEXT NOT NULL,
                blockchainTypeUid TEXT NOT NULL,
                tokenTypeId TEXT NOT NULL,
                meta TEXT,
                amountAtomic TEXT NOT NULL,
                feeAtomic TEXT,
                sdkBalanceAtCreationAtomic TEXT NOT NULL,
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
                id, walletId, coinUid, blockchainTypeUid, tokenTypeId, meta,
                amountAtomic, feeAtomic, sdkBalanceAtCreationAtomic,
                fromAddress, toAddress, txHash, nonce, memo, createdAt, expiresAt
            )
            SELECT
                id, walletId, coinUid, blockchainTypeUid, COALESCE(tokenTypeId, 'native'), meta,
                amountAtomic, feeAtomic, sdkBalanceAtCreationAtomic,
                fromAddress, toAddress, txHash, nonce, memo, createdAt, expiresAt
            FROM PendingTransaction
        """
        )

        db.execSQL("DROP TABLE PendingTransaction")

        db.execSQL("ALTER TABLE PendingTransaction_new RENAME TO PendingTransaction")
    }
}

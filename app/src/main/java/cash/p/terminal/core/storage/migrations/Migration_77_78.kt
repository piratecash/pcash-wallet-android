package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_77_78 : Migration(77, 78) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE PendingTransaction (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                coinUid TEXT NOT NULL,
                blockchainTypeUid TEXT NOT NULL,
                tokenTypeId TEXT,
                amountAtomic TEXT NOT NULL,
                feeAtomic TEXT,
                fromAddress TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                utxoInputsJson TEXT,
                txHash TEXT,
                nonce INTEGER,
                memo TEXT,
                createdAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL
            )
        """
        )
    }
}

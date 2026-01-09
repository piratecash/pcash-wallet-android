package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_89_90 : Migration(89, 90) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add sdkBalanceAtCreationAtomic column to PendingTransaction table
        // Default "0" for any existing records (which should be none in practice)
        db.execSQL("ALTER TABLE PendingTransaction ADD COLUMN sdkBalanceAtCreationAtomic TEXT NOT NULL DEFAULT '0'")
    }
}

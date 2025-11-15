package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_79_80 : Migration(79, 80) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE PendingTransaction ADD COLUMN meta TEXT")
    }
}

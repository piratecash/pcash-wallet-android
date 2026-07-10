package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_106_107 : Migration(106, 107) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN tokenQueryId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN sourceTokenQueryId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN coinUid TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN coinName TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN feeTokenQueryId TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN feeAtomic TEXT")
    }
}

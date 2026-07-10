package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_105_106 : Migration(105, 106) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN status TEXT NOT NULL DEFAULT 'pending'")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN broadcastAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN lastBroadcastAt INTEGER")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN broadcastedAt INTEGER")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN lastError TEXT")
    }
}

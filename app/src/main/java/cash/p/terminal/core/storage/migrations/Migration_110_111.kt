package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_110_111 : Migration(110, 111) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN stellarSourceAccountId TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN stellarSequenceNumber INTEGER")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN stellarValidUntil INTEGER")
    }
}

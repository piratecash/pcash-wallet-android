package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_108_109 : Migration(108, 109) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN tonValidUntil INTEGER")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN tonSenderAddress TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN tonSeqno INTEGER")
    }
}

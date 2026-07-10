package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_107_108 : Migration(107, 108) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN solanaBlockHash TEXT")
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN solanaLastValidBlockHeight INTEGER")
    }
}

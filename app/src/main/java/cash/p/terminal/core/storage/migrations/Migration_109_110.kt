package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_109_110 : Migration(109, 110) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE OfflineSignedTransaction ADD COLUMN tronExpiration INTEGER")
    }
}

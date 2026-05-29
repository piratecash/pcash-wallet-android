package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_103_104 : Migration(103, 104) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("SwapProviderTransaction", "accountId")) {
            db.execSQL(
                "ALTER TABLE SwapProviderTransaction ADD COLUMN accountId TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
        val cursor = query("PRAGMA table_info($table)")
        return try {
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
                    return true
                }
            }
            false
        } finally {
            cursor.close()
        }
    }
}

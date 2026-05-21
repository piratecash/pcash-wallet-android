package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_101_102 : Migration(101, 102) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA table_info(SwapProviderTransaction)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        if ("accountId" !in columns) {
            db.execSQL(
                "ALTER TABLE SwapProviderTransaction ADD COLUMN accountId TEXT NOT NULL DEFAULT ''"
            )
        }
    }
}

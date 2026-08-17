package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_114_115 : Migration(114, 115) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `OfflineBlockchain` (
                `accountId` TEXT NOT NULL,
                `blockchainType` TEXT NOT NULL,
                `offline` INTEGER NOT NULL,
                `enabledAt` INTEGER,
                `lastSyncedAt` INTEGER,
                PRIMARY KEY(`accountId`, `blockchainType`),
                FOREIGN KEY(`accountId`) REFERENCES `AccountRecord`(`id`) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
    }
}

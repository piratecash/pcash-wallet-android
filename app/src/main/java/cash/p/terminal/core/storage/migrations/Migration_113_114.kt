package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_113_114 : Migration(113, 114) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `AddressLabel` (
                `scope` TEXT NOT NULL,
                `normalizedAddress` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                PRIMARY KEY(`scope`, `normalizedAddress`, `source`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `AddressLabel` (`scope`, `normalizedAddress`, `source`, `label`)
            SELECT 'evm', lower(`address`), 'LEGACY_API', `label`
            FROM `EvmAddressLabel`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `EvmAddressLabel`")
    }
}

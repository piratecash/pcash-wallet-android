package cash.p.terminal.wallet.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_14_15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM TokenEntity
            WHERE blockchainUid NOT IN (SELECT uid FROM BlockchainEntity)
            """.trimIndent()
        )
    }
}

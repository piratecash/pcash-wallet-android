package cash.p.terminal.wallet.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_14_15 : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        deleteOrphanTokens(database)
    }
}

internal fun deleteOrphanTokens(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        DELETE FROM TokenEntity
        WHERE blockchainUid NOT IN (SELECT uid FROM BlockchainEntity)
            OR coinUid NOT IN (SELECT uid FROM Coin)
        """.trimIndent()
    )
}

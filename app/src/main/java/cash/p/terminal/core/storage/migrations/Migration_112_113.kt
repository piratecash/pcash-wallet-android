package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_112_113 : Migration(112, 113) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Byte-equivalent to the statement Room generates for WalletConnectV2PairingMetadata, so
        // Room's TableInfo validation on open (exportSchema=false) passes after the upgrade.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `WalletConnectV2PairingMetadata` " +
                "(`topic` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                "`icon` TEXT, PRIMARY KEY(`topic`))"
        )
    }
}

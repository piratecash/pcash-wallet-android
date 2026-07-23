package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_111_112 : Migration(111, 112) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Canonical inbound (deposit/burn) tx hash — required by the Unstoppable /track call for EVM
        // sub-providers; nullable, harmless for existing providers.
        db.execSQL(
            "ALTER TABLE SwapProviderTransaction ADD COLUMN depositTransactionHash TEXT"
        )
        // Unstoppable sub-provider api id — drives the per-sub-provider display name in history for the
        // single SwapProvider.UNSTOPPABLE backend; null for all non-Unstoppable rows.
        db.execSQL(
            "ALTER TABLE SwapProviderTransaction ADD COLUMN unstoppableSubProviderId TEXT"
        )
    }
}

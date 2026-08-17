package cash.p.terminal.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import cash.p.terminal.wallet.entities.AccountRecord
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Offline mode of one (account, blockchain) pair. The mode is the [offline] column, not the mere
 * existence of the row: [lastSyncedAt] is written while the pair is online too, so the row appears
 * long before offline mode is ever enabled. Both timestamps are epoch millis.
 */
@Entity(
    primaryKeys = ["accountId", "blockchainType"],
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class OfflineBlockchain(
    val accountId: String,
    val blockchainType: BlockchainType,
    val offline: Boolean,
    val enabledAt: Long?,
    val lastSyncedAt: Long?,
)

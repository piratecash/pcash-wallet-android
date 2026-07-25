package cash.p.terminal.modules.walletconnect.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WalletConnectV2PairingMetadata(
    @PrimaryKey val topic: String,
    val name: String,
    val url: String,
    val icon: String?,
)

package cash.p.terminal.entities

import cash.p.terminal.R
import io.horizontalsystems.core.entities.BlockchainType

enum class BtcRestoreMode(val raw: String) {
    Blockchair("blockchair"),
    Hybrid("hybrid"),
    Blockchain("blockchain");

    fun getTitle(blockchainType: BlockchainType): Int = when (this) {
        Blockchair -> {
            when (blockchainType) {
                BlockchainType.Cosanta -> {
                    R.string.SettingsSecurity_SyncModeCosantaExplorer
                }
                BlockchainType.PirateCash -> {
                    R.string.SettingsSecurity_SyncModePirateCashExplorer
                }
                else -> {
                    R.string.SettingsSecurity_SyncModeBlockchair
                }
            }
        }

        Hybrid -> R.string.SettingsSecurity_SyncModeHybrid
        Blockchain -> R.string.SettingsSecurity_SyncModeBlockchain
    }

    val description: Int
        get() = when (this) {
            Blockchair -> R.string.SettingsSecurity_SyncModeBlockchairDescription
            Hybrid -> R.string.SettingsSecurity_SyncModeHybridDescription
            Blockchain -> R.string.SettingsSecurity_SyncModeBlockchainDescription
        }

}

package cash.p.terminal.entities

import androidx.room.Entity

@Entity(primaryKeys = ["scope", "normalizedAddress", "source"])
data class AddressLabel(
    val scope: String,
    val normalizedAddress: String,
    val source: AddressLabelSource,
    val label: String,
)

enum class AddressLabelSource(val priority: Int) {
    BUILT_IN(0),
    REMOTE(1),
    LEGACY_API(2),
}

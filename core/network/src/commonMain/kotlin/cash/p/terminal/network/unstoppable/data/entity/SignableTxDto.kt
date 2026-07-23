package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// A signable transaction the server built. `kind` tags the shape; each per-chain builder reads
// only the fields the method uses (evm: to/value/data/gas).
@Serializable
internal data class SignableTxDto(
    val kind: String,
    val to: String? = null,
    val from: String? = null,
    val value: String? = null,
    val data: String? = null,
    val gas: String? = null,
    val gasPrice: String? = null,
    val message: String? = null,
    val tx: JsonElement? = null,
)

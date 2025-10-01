package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class SwapStatusDto(
    val address: String,
    val query_id: String,
    val exit_code: String,
    val coins: String,
    val logical_time: String,
    val tx_hash: List<Int>,
    val balance_deltas: String
)

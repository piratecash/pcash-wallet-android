package cash.p.terminal.network.pirate.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class CoinsPriceChangeRequest(
    val uids: List<String>,
    val currency: String
)
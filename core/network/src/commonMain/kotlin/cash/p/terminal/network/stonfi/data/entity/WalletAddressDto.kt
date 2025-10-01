package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class WalletAddressDto(
    val address: String
)

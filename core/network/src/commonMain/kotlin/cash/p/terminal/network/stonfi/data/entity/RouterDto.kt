package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RouterDto(
    val address: String,
    @SerialName("major_version")
    val majorVersion: Int,
    @SerialName("minor_version")
    val minorVersion: Int,
    @SerialName("pton_master_address")
    val ptonMasterAddress: String,
    @SerialName("pton_wallet_address")
    val ptonWalletAddress: String,
    @SerialName("pton_version")
    val ptonVersion: String,
    @SerialName("router_type")
    val routerType: String,
    @SerialName("pool_creation_enabled")
    val poolCreationEnabled: Boolean
)

@Serializable
internal data class RouterResponseDto(
    val router: RouterDto
)

package cash.p.terminal.network.stonfi.domain.entity

data class RouterInfo(
    val address: String,
    val majorVersion: Int,
    val minorVersion: Int,
    val ptonMasterAddress: String,
    val ptonWalletAddress: String,
    val ptonVersion: String,
    val routerType: String,
    val poolCreationEnabled: Boolean
)

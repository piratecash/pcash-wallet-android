package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class ProviderDto(
    val provider: String,
    val name: String? = null,
    val supportedChainIds: List<String> = emptyList(),
    val amlPolicy: String? = null,
    val amlPolicyDescription: String? = null,
    val contacts: ProviderContactsDto? = null,
)

@Serializable
internal data class ProviderContactsDto(
    val email: String? = null,
    val telegram: String? = null,
    val twitter: String? = null,
    val website: String? = null,
)

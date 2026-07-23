package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class TokensDto(
    val tokens: List<TokenDto> = emptyList(),
    val supportedChainIds: List<String> = emptyList(),
)

@Serializable
internal data class TokenDto(
    val chain: String,
    val chainId: String,
    val address: String? = null,
    val identifier: String,
)

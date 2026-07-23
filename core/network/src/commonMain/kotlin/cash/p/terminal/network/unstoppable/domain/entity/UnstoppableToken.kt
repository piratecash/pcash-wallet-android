package cash.p.terminal.network.unstoppable.domain.entity

data class UnstoppableProviderTokens(
    val tokens: List<UnstoppableToken>,
    val supportedChainIds: List<String>,
)

data class UnstoppableToken(
    val chain: String,
    val chainId: String,
    val address: String?,
    val identifier: String,
)

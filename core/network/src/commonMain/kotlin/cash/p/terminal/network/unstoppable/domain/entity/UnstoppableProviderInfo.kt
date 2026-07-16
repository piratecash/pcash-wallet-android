package cash.p.terminal.network.unstoppable.domain.entity

data class UnstoppableProviderInfo(
    val provider: String,
    val name: String?,
    val supportedChainIds: List<String>,
    val amlPolicy: String?,
    val amlPolicyDescription: String?,
    val contacts: UnstoppableProviderContacts?,
)

data class UnstoppableProviderContacts(
    val email: String?,
    val telegram: String?,
    val twitter: String?,
    val website: String?,
)

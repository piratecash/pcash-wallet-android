package cash.p.terminal.network.stonfi.domain.entity

data class SwapStatus(
    val address: String,
    val queryId: String,
    val exitCode: String,
    val coins: String,
    val logicalTime: String,
    val txHash: List<Int>,
    val balanceDeltas: String
)

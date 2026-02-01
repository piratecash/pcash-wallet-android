package cash.p.terminal.feature.miniapp.data.api

import kotlinx.serialization.Serializable

@Serializable
data class BalanceRequestDto(
    val uniqueCode: String,
    val evmAddress: String,
    val apiVersion: Int
)

@Serializable
data class BalanceResponseDto(
    val balance: String
)

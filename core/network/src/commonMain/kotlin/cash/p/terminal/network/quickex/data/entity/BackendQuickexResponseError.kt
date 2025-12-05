package cash.p.terminal.network.quickex.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class BackendQuickexResponseError(
    val status: String?,
    override val message: String?,
    val data: ErrorData? = null
): Throwable() {
    companion object {
        const val DEPOSIT_TOO_SMALL = "ERR_CLAIMED_DEPOSIT_AMOUNT_TOO_SMALL"
    }

    override fun toString(): String {
        return "BackendQuickexResponseError(status=$status, message=$message, data=$data)"
    }
}

@Serializable
data class ErrorData(
    val localizedMessage: String? = null,
    val details: ErrorDetails? = null
)

@Serializable
data class ErrorDetails(
    val field: String? = null,
    val value: String? = null,
    val expected: String? = null,
    val expectedGeneral: String? = null
)
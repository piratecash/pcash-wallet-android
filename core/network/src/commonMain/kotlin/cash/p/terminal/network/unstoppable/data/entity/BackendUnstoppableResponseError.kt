package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class BackendUnstoppableResponseError(
    val error: String? = null,
    override val message: String? = null,
    val statusCode: Int? = null,
    val errors: JsonElement? = null,
) : Throwable() {
    override fun toString(): String {
        return "BackendUnstoppableResponseError(error=$error, message=$message, statusCode=$statusCode, errors=$errors)"
    }
}

package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun TrezorResponse.requirePayload(): JsonObject {
    if (!success) {
        val code = payload?.jsonObject?.get("code")?.jsonPrimitive?.content
        if (code == "Method_Cancel") throw TrezorCancelledException()
        throw TrezorSigningException(error ?: "Unknown Trezor error")
    }
    return requireNotNull(payload?.jsonObject) { "Trezor response has no payload" }
}

fun JsonObject.hexString(key: String): String =
    getValue(key).jsonPrimitive.content

fun JsonObject.hexBytes(key: String): ByteArray =
    hexString(key).removePrefix("0x").hexToByteArray()

fun JsonObject.hexInt(key: String): Int =
    hexString(key).removePrefix("0x").toInt(16)

private fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

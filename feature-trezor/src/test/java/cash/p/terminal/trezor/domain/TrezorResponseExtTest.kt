package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrezorResponseExtTest {

    @Test
    fun requirePayload_successWithPayload_returnsJsonObject() {
        val payload = buildJsonObject { put("v", "0x1c") }
        val response = TrezorResponse(success = true, payload = payload)
        val result = response.requirePayload()
        assertEquals("0x1c", result.hexString("v"))
    }

    @Test
    fun requirePayload_failureWithError_throwsTrezorSigningException() {
        val response = TrezorResponse(success = false, error = "User cancelled")
        val ex = assertThrows(TrezorSigningException::class.java) {
            response.requirePayload()
        }
        assertEquals("User cancelled", ex.message)
    }

    @Test
    fun requirePayload_failureNoError_throwsWithDefaultMessage() {
        val response = TrezorResponse(success = false)
        val ex = assertThrows(TrezorSigningException::class.java) {
            response.requirePayload()
        }
        assertEquals("Unknown Trezor error", ex.message)
    }

    @Test
    fun requirePayload_methodCancel_throwsTrezorCancelledException() {
        val payload = buildJsonObject {
            put("error", "Canceled")
            put("code", "Method_Cancel")
        }
        val response = TrezorResponse(success = false, payload = payload)
        assertThrows(TrezorCancelledException::class.java) {
            response.requirePayload()
        }
    }

    @Test
    fun requirePayload_successNoPayload_throwsIllegalArgument() {
        val response = TrezorResponse(success = true, payload = null)
        assertThrows(IllegalArgumentException::class.java) {
            response.requirePayload()
        }
    }

    @Test
    fun hexString_returnsRawContent() {
        val obj = buildJsonObject { put("r", "0xabcd") }
        assertEquals("0xabcd", obj.hexString("r"))
    }

    @Test
    fun hexBytes_stripsPrefix_decodesBytes() {
        val obj = buildJsonObject { put("s", "0xabcd") }
        val bytes = obj.hexBytes("s")
        assertArrayEquals(byteArrayOf(0xab.toByte(), 0xcd.toByte()), bytes)
    }

    @Test
    fun hexInt_parsesHexString() {
        val obj = buildJsonObject { put("v", "0x1c") }
        assertEquals(28, obj.hexInt("v"))
    }

    @Test
    fun hexInt_parsesWithoutPrefix() {
        val obj = buildJsonObject { put("v", "1c") }
        assertEquals(28, obj.hexInt("v"))
    }
}

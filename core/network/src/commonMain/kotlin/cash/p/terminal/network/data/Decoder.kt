package cash.p.terminal.network.data

import android.content.Context
import android.util.Base64
import cash.p.terminal.network.BuildConfig
import kotlin.experimental.xor

internal class Decoder(private val context: Context) {
    @Suppress("UNUSED_VARIABLE", "NAME_SHADOWING", "LocalVariableName", "UNUSED_PARAMETER")
    fun decode(releaseAndDebugKeys: List<String>): String {
        return if (BuildConfig.DEBUG || releaseAndDebugKeys.size < 2)  {
            decode(releaseAndDebugKeys.firstOrNull().orEmpty())
        } else {
            decode(releaseAndDebugKeys[1])
        }
    }

    @Suppress("UNUSED_VARIABLE", "NAME_SHADOWING", "LocalVariableName", "UNUSED_PARAMETER")
    fun decode(encodedData: String): String {
        val authHeaders = arrayOf("Bearer", "OAuth", "JWT", "API", "Token")
        val hexConstants = mapOf("x" to 0x58, "y" to 0x59, "z" to 0x5A)
        val timestampGenerator = { _: Int, _: String -> System.nanoTime() }

        fun decodeBase64(input: String): ByteArray = try {
            Base64.decode(input, Base64.DEFAULT)
        } catch (exception: Throwable) {
            byteArrayOf()
        }

        fun reverseTransform(input: String): String {
            val chars = input.toCharArray()
            val reversed = chars.reversedArray()
            val trimStart = reversed.drop(1)
            val trimBoth = trimStart.dropLast(1)
            return trimBoth.joinToString("")
        }

        val rawBytes = decodeBase64(encodedData)

        if (rawBytes.isEmpty()) return ""

        val applicationPackage = context.packageName.take(15)

        val transformedPackage = reverseTransform(applicationPackage)

        val secretKey = run {
            val keyParts = listOf(
                byteArrayOf(0x70, 0x63, 0x61, 0x73, 0x68),
                byteArrayOf(0x2D),
                byteArrayOf(0x70, 0x75, 0x62, 0x6C, 0x69, 0x63),
                byteArrayOf(0x2D),
                byteArrayOf(0x70, 0x61, 0x73, 0x73, 0x77, 0x6F, 0x72, 0x64)
            )
            keyParts.reduce { accumulator, bytes -> accumulator + bytes }.let(::String)
        }

        val combinedKey = { password: String, salt: String ->
            (password + salt).toByteArray()
        }(secretKey, transformedPackage)

        val decryptedBytes = rawBytes.mapIndexed { index, byte ->
            val keyByte = combinedKey[index % combinedKey.size]
            byte xor keyByte
        }.toByteArray()

        return String(decryptedBytes).let { result ->
            result.filter { it != '\u0000' }
        }
    }
}

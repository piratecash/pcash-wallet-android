package cash.p.terminal.core.managers

import android.util.Base64
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineFeeMetadata
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineTokenMetadata
import cash.p.terminal.entities.OfflineTransactionOutpoint
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

class OfflineTransactionPayloadEncoder {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(draft: OfflineSignedTransactionDraft): String {
        val blockchainUid = draft.wallet.token.blockchainType.uid
        val feeToken = draft.feeToken ?: draft.wallet.token
        val payload = Payload(
            blockchainUid = blockchainUid,
            rawHex = draft.rawHex.lowercase(),
            txHash = draft.txHash,
            token = draft.wallet.token.toPayloadToken(),
            amountAtomic = draft.amount.movePointRight(draft.wallet.token.decimals).toBigInteger().toString(),
            fee = draft.fee?.let {
                PayloadFee(
                    tokenQueryId = feeToken.tokenQuery.id,
                    atomic = it.movePointRight(feeToken.decimals).toBigInteger().toString(),
                    decimals = feeToken.decimals,
                )
            },
            toAddress = draft.toAddress,
            createdAt = draft.createdAt,
            inputOutpoints = draft.inputOutpoints,
            solanaRetry = draft.solanaRetryMetadata?.toPayload(),
            checksum = checksum(draft.rawHex),
        )
        return listOf(SCHEME, TYPE, VERSION, blockchainUid, compressedBase64(payload))
            .joinToString(separator = ":")
    }

    // Inverse of [encode]: parses a pcash:tx:v1:<blockchainUid>:<compressedBase64> payload.
    // Returns null for any malformed input or checksum mismatch so callers can fall back to
    // treating the input as a plain raw-hex transaction.
    fun decode(payload: String): DecodedOfflineTransaction? {
        val parts = payload.trim().split(":", limit = 5)
        if (parts.size != 5) return null
        if (parts[0] != SCHEME || parts[1] != TYPE || parts[2] != VERSION) return null
        val blockchainUid = parts[3]
        val body = parts[4]

        val decoded = tryOrNull {
            val compressed = Base64.decode(body, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            json.decodeFromString<Payload>(decompress(compressed).decodeToString())
        } ?: return null

        if (!isValidPayload(decoded, blockchainUid)) return null

        return DecodedOfflineTransaction(
            // The path segment is authoritative: isValidPayload already rejected a body whose
            // blockchainUid disagrees, so the network shown/used cannot be spoofed by the body.
            blockchainUid = blockchainUid,
            rawHex = decoded.rawHex,
            txHash = decoded.txHash,
            token = decoded.token.toMetadata(),
            amountAtomic = decoded.amountAtomic,
            fee = decoded.fee?.toMetadata(),
            toAddress = decoded.toAddress,
            createdAt = decoded.createdAt,
            inputOutpoints = decoded.inputOutpoints,
            solanaRetryMetadata = decoded.solanaRetry?.toMetadata(),
        )
    }

    private fun compressedBase64(payload: Payload): String {
        val bytes = json.encodeToString(payload).encodeToByteArray()
        val compressed = compress(bytes)
        return Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun compress(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val output = ByteArrayOutputStream(bytes.size)
            val buffer = ByteArray(COMPRESSION_BUFFER_SIZE)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun decompress(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val initialCapacity = (bytes.size * INFLATE_SIZE_HINT).coerceAtMost(MAX_DECOMPRESSED_SIZE)
            val output = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(COMPRESSION_BUFFER_SIZE)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                // The whole input is fed at once, so a complete stream only stops by finishing. Any zero
                // read means truncation or a required preset dictionary: stop instead of looping forever.
                if (count == 0) break
                require(output.size() + count <= MAX_DECOMPRESSED_SIZE) {
                    "Decompressed offline payload exceeds the allowed size"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    // The checksum only authenticates rawHex — the bytes that are actually broadcast. Every other
    // field is untrusted display metadata, so reject anything we cannot interpret and keep rawHex as
    // the single source of truth for broadcast.
    private fun isValidPayload(payload: Payload, blockchainUid: String): Boolean =
        payload.version == VERSION_INT &&
            payload.encoding == RAW_HEX_ENCODING &&
            payload.blockchainUid == blockchainUid &&
            isHex(payload.rawHex) &&
            payload.checksum == checksum(payload.rawHex) &&
            isTxHash(payload.txHash, blockchainUid) &&
            isValidToken(payload.token, blockchainUid) &&
            isValidFee(payload.fee, blockchainUid) &&
            isValidSolanaRetry(payload.solanaRetry, blockchainUid) &&
            payload.toAddress.isNotBlank() &&
            isNonNegativeAtomic(payload.amountAtomic)

    private fun isValidToken(token: PayloadToken, blockchainUid: String): Boolean {
        val query = TokenQuery.fromId(token.tokenQueryId) ?: return false
        return query.blockchainType.uid == blockchainUid &&
            token.coinCode.isNotBlank() &&
            token.decimals >= 0
    }

    private fun isValidFee(fee: PayloadFee?, blockchainUid: String): Boolean {
        fee ?: return true
        val query = TokenQuery.fromId(fee.tokenQueryId) ?: return false
        return query.blockchainType.uid == blockchainUid &&
            fee.decimals >= 0 &&
            isNonNegativeAtomic(fee.atomic)
    }

    private fun isTxHash(value: String, blockchainUid: String): Boolean =
        if (blockchainUid == BlockchainType.Solana.uid) {
            isSolanaSignature(value)
        } else {
            // A Bitcoin/EVM txid is a 32-byte hash, i.e. exactly 64 hex characters; anything else means
            // the signer wrote a value the wallet would never produce, so reject it instead of keying a
            // record by it.
            value.length == TX_HASH_HEX_LENGTH && isHex(value)
        }

    private fun isSolanaSignature(value: String): Boolean =
        tryOrNull { Base58.decode(value).size == SOLANA_SIGNATURE_BYTES } == true

    private fun isValidSolanaRetry(
        solanaRetry: PayloadSolanaRetry?,
        blockchainUid: String,
    ): Boolean =
        if (blockchainUid == BlockchainType.Solana.uid) {
            solanaRetry != null &&
                solanaRetry.blockHash.isNotBlank() &&
                solanaRetry.lastValidBlockHeight > 0
        } else {
            solanaRetry == null
        }

    private fun isNonNegativeAtomic(value: String): Boolean =
        (value.toBigIntegerOrNull()?.signum() ?: -1) >= 0

    private fun checksum(rawHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawHex.lowercase().encodeToByteArray())
        return Base64.encodeToString(digest.copyOf(CHECKSUM_BYTES), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun Token.toPayloadToken() = PayloadToken(
        tokenQueryId = tokenQuery.id,
        coinUid = coin.uid,
        coinCode = coin.code,
        coinName = coin.name,
        decimals = decimals,
    )

    private fun PayloadToken.toMetadata() = OfflineTokenMetadata(
        tokenQueryId = tokenQueryId,
        coinUid = coinUid,
        coinCode = coinCode,
        coinName = coinName,
        decimals = decimals,
    )

    private fun PayloadFee.toMetadata() = OfflineFeeMetadata(
        tokenQueryId = tokenQueryId,
        atomic = atomic,
        decimals = decimals,
    )

    private fun OfflineSolanaRetryMetadata.toPayload() = PayloadSolanaRetry(
        blockHash = blockHash,
        lastValidBlockHeight = lastValidBlockHeight,
    )

    private fun PayloadSolanaRetry.toMetadata() = OfflineSolanaRetryMetadata(
        blockHash = blockHash,
        lastValidBlockHeight = lastValidBlockHeight,
    )

    @Serializable
    private data class Payload(
        val version: Int = 1,
        val blockchainUid: String,
        val encoding: String = RAW_HEX_ENCODING,
        val rawHex: String,
        val txHash: String,
        val token: PayloadToken,
        val amountAtomic: String,
        val fee: PayloadFee?,
        val toAddress: String,
        val createdAt: Long,
        val inputOutpoints: List<OfflineTransactionOutpoint>,
        val checksum: String,
        val solanaRetry: PayloadSolanaRetry? = null,
    )

    @Serializable
    private data class PayloadToken(
        val tokenQueryId: String,
        val coinUid: String?,
        val coinCode: String,
        val coinName: String?,
        val decimals: Int,
    )

    @Serializable
    private data class PayloadFee(
        val tokenQueryId: String,
        val atomic: String,
        val decimals: Int,
    )

    @Serializable
    private data class PayloadSolanaRetry(
        val blockHash: String,
        val lastValidBlockHeight: Long,
    )

    companion object {
        private const val SCHEME = "pcash"
        private const val TYPE = "tx"
        private const val VERSION = "v1"

        // Cheap prefix check so the global scanner can route a P.CASH payload to broadcast
        // without injecting the encoder or running a full decode.
        const val PAYLOAD_PREFIX = "$SCHEME:$TYPE:"

        fun isOfflineTransactionPayload(text: String): Boolean =
            text.trim().startsWith(PAYLOAD_PREFIX)

        fun isRawTransactionHex(text: String): Boolean =
            text.trim().let { value ->
                value.length >= MIN_RAW_HEX_LENGTH && isHex(value)
            }

        private fun isHex(value: String): Boolean =
            value.isNotEmpty() && value.length % 2 == 0 &&
                value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private const val VERSION_INT = 1
        private const val RAW_HEX_ENCODING = "rawhex"
        private const val CHECKSUM_BYTES = 8
        private const val TX_HASH_HEX_LENGTH = 64
        private const val SOLANA_SIGNATURE_BYTES = 64
        private const val MIN_RAW_HEX_LENGTH = 20
        private const val COMPRESSION_BUFFER_SIZE = 512
        private const val INFLATE_SIZE_HINT = 4

        // Upper bound for the inflated payload. A signed wallet transaction is a few KB, so 1 MB is far
        // beyond any legitimate payload while still capping a crafted "zip bomb" from exhausting memory.
        private const val MAX_DECOMPRESSED_SIZE = 1 * 1024 * 1024
    }
}

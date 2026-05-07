package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.hexString
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.requirePayload
import io.horizontalsystems.stellarkit.Signer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.stellar.sdk.Asset
import org.stellar.sdk.AssetTypeCreditAlphaNum
import org.stellar.sdk.AssetTypeCreditAlphaNum12
import org.stellar.sdk.AssetTypeCreditAlphaNum4
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.Memo
import org.stellar.sdk.MemoHash
import org.stellar.sdk.MemoId
import org.stellar.sdk.MemoNone
import org.stellar.sdk.MemoReturnHash
import org.stellar.sdk.MemoText
import org.stellar.sdk.Transaction
import org.stellar.sdk.operations.ChangeTrustOperation
import org.stellar.sdk.operations.CreateAccountOperation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.xdr.DecoratedSignature
import org.stellar.sdk.xdr.Signature
import org.stellar.sdk.xdr.SignatureHint
import java.math.BigDecimal

class TrezorStellarSigner(
    override val publicKey: ByteArray,
    private val derivationPath: String,
    private val networkPassphrase: String,
    private val deepLinkManager: TrezorDeepLinkManager
) : Signer {

    /**
     * Holds the pending transaction so that [sign] can access the full
     * structured transaction when StellarKit calls `sign(hash)`.
     * This is needed because the published StellarKit calls `sign(hash)` only,
     * but Trezor requires the full transaction structure.
     * Once StellarKit is updated to call `signTransaction(transaction)` first,
     * this field becomes unused.
     */
    @Volatile
    private var pendingTransaction: Transaction? = null

    override fun canSign() = true

    /**
     * Prepares a transaction for signing. Must be called before StellarKit
     * invokes [sign] so that the full transaction data is available.
     */
    fun prepareTransaction(transaction: Transaction) {
        pendingTransaction = transaction
    }

    override suspend fun sign(hash: ByteArray): DecoratedSignature {
        val transaction = requireNotNull(pendingTransaction) {
            "No pending transaction. Call prepareTransaction() before sign(), " +
                "or update stellar-kit to a version that supports signTransaction()."
        }
        pendingTransaction = null
        return signViaTrezor(transaction)
    }

    // Will become `override` once stellar-kit publishes signTransaction in Signer interface
    override suspend fun signTransaction(transaction: Transaction): DecoratedSignature {
        return signViaTrezor(transaction)
    }

    private suspend fun signViaTrezor(transaction: Transaction): DecoratedSignature {
        val params = buildJsonObject {
            put("path", derivationPath)
            put("networkPassphrase", networkPassphrase)
            put("transaction", buildTransactionJson(transaction))
        }
        val response = deepLinkManager.call(TrezorMethod.XlmSignTransaction, params)
        val payload = response.requirePayload()
        val signatureBytes = payload.hexString("signature")
            .removePrefix("0x")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        return buildDecoratedSignature(signatureBytes)
    }

    private fun buildDecoratedSignature(signatureBytes: ByteArray): DecoratedSignature {
        val hint = SignatureHint().apply {
            signatureHint = publicKey.copyOfRange(publicKey.size - 4, publicKey.size)
        }
        val sig = Signature().apply {
            signature = signatureBytes
        }
        return DecoratedSignature().apply {
            this.hint = hint
            this.signature = sig
        }
    }

    private fun buildTransactionJson(transaction: Transaction): JsonObject = buildJsonObject {
        put("source", transaction.sourceAccount)
        put("fee", transaction.fee.toInt())
        put("sequence", transaction.sequenceNumber.toString())
        transaction.preconditions?.timeBounds?.let {
            put("timebounds", buildJsonObject {
                put("minTime", it.minTime.toLong())
                put("maxTime", it.maxTime.toLong())
            })
        }
        put("memo", buildMemoJson(transaction.memo))
        put("operations", buildOperationsJson(transaction))
    }

    private fun buildMemoJson(memo: Memo): JsonObject = buildJsonObject {
        when (memo) {
            is MemoNone -> put("type", 0)
            is MemoText -> {
                put("type", 1)
                put("text", memo.text)
            }
            is MemoId -> {
                put("type", 2)
                put("id", memo.id.toString())
            }
            is MemoHash -> {
                put("type", 3)
                put("hash", memo.hexValue)
            }
            is MemoReturnHash -> {
                put("type", 4)
                put("hash", memo.hexValue)
            }
        }
    }

    private fun buildOperationsJson(transaction: Transaction) = buildJsonArray {
        for (op in transaction.operations) {
            add(buildOperationJson(op))
        }
    }

    private fun buildOperationJson(operation: org.stellar.sdk.operations.Operation): JsonObject =
        when (operation) {
            is PaymentOperation -> buildPaymentJson(operation)
            is CreateAccountOperation -> buildCreateAccountJson(operation)
            is ChangeTrustOperation -> buildChangeTrustJson(operation)
            else -> throw UnsupportedOperationException(
                "Trezor does not support Stellar operation: ${operation.javaClass.simpleName}"
            )
        }

    private fun buildPaymentJson(op: PaymentOperation) = buildJsonObject {
        put("type", "payment")
        put("destination", op.destination)
        put("amount", toStroops(op.amount))
        put("asset", buildAssetJson(op.asset))
        op.sourceAccount?.let { put("source", it) }
    }

    private fun buildCreateAccountJson(op: CreateAccountOperation) = buildJsonObject {
        put("type", "createAccount")
        put("destination", op.destination)
        put("startingBalance", toStroops(op.startingBalance))
        op.sourceAccount?.let { put("source", it) }
    }

    private fun buildChangeTrustJson(op: ChangeTrustOperation) = buildJsonObject {
        put("type", "changeTrust")
        put("limit", toStroops(op.limit))
        val asset = op.asset.asset
        if (asset != null) {
            put("line", buildAssetJson(asset))
        }
        op.sourceAccount?.let { put("source", it) }
    }

    private fun buildAssetJson(asset: Asset) = buildJsonObject {
        when (asset) {
            is AssetTypeNative -> put("type", 0)
            is AssetTypeCreditAlphaNum4 -> {
                put("type", 1)
                put("code", asset.code)
                put("issuer", asset.issuer)
            }
            is AssetTypeCreditAlphaNum12 -> {
                put("type", 2)
                put("code", asset.code)
                put("issuer", asset.issuer)
            }
            is AssetTypeCreditAlphaNum -> {
                val type = if (asset.code.length <= 4) 1 else 2
                put("type", type)
                put("code", asset.code)
                put("issuer", asset.issuer)
            }
        }
    }

    private fun toStroops(amount: BigDecimal): String =
        amount.movePointRight(7).toBigInteger().toString()
}

package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorStellarAsset
import cash.p.terminal.trezorkit.client.TrezorStellarMemo
import cash.p.terminal.trezorkit.client.TrezorStellarOperation
import cash.p.terminal.trezorkit.client.TrezorStellarSignTx
import io.horizontalsystems.stellarkit.Signer
import org.stellar.sdk.Asset
import org.stellar.sdk.AssetTypeCreditAlphaNum12
import org.stellar.sdk.AssetTypeCreditAlphaNum4
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.ChangeTrustAsset
import org.stellar.sdk.Memo
import org.stellar.sdk.MemoHash
import org.stellar.sdk.MemoId
import org.stellar.sdk.MemoNone
import org.stellar.sdk.MemoReturnHash
import org.stellar.sdk.MemoText
import org.stellar.sdk.Transaction
import org.stellar.sdk.operations.ChangeTrustOperation
import org.stellar.sdk.operations.CreateAccountOperation
import org.stellar.sdk.operations.Operation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.xdr.DecoratedSignature
import org.stellar.sdk.xdr.Signature
import org.stellar.sdk.xdr.SignatureHint
import java.math.BigDecimal
import java.math.BigInteger

class TrezorStellarSigner(
    override val publicKey: ByteArray,
    private val derivationPath: String,
    private val networkPassphrase: String,
    private val trezorClient: ITrezorClient
) : Signer {

    /**
     * Holds the pending transaction so that [sign] can access the full structured transaction when
     * StellarKit calls `sign(hash)`. Trezor needs the whole transaction, not just its hash; this
     * field bridges the older `sign(hash)`-only StellarKit contract and becomes unused once every
     * caller invokes [signTransaction] first.
     */
    @Volatile
    private var pendingTransaction: Transaction? = null

    override fun canSign() = true

    /**
     * Prepares a transaction for signing. Must be called before StellarKit invokes [sign] so that
     * the full transaction data is available.
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

    override suspend fun signTransaction(transaction: Transaction): DecoratedSignature {
        return signViaTrezor(transaction)
    }

    private suspend fun signViaTrezor(transaction: Transaction): DecoratedSignature {
        val signTx = transaction.toTrezorSignTx()
        val signatureBytes = trezorClient.connect { signStellar(signTx) }
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

    private fun Transaction.toTrezorSignTx(): TrezorStellarSignTx {
        rejectUnsupported()
        val bounds = timeBounds
        return TrezorStellarSignTx(
            addressN = TrezorDerivationPath.parse(derivationPath),
            networkPassphrase = networkPassphrase,
            source = sourceAccount,
            fee = fee.toInt(),
            sequenceNumber = sequenceNumber,
            timeboundsStart = bounds?.minTime.toTimeboundLong("timebounds start"),
            timeboundsEnd = bounds?.maxTime.toTimeboundLong("timebounds end"),
            memo = memo.toTrezorMemo(),
            operations = operations.map { it.toTrezorOperation() }
        )
    }

    /**
     * Rejects anything [TrezorStellarSignTx] cannot faithfully represent. A silent drop would let the
     * device sign a transaction that differs from the caller's XDR, producing a valid-looking but
     * wrong signature - fatal for raw-XDR/WalletConnect flows that submit the original envelope.
     */
    private fun Transaction.rejectUnsupported() {
        if (isSorobanTransaction) {
            throw UnsupportedOperationException("Trezor does not support Soroban Stellar transactions")
        }
        if (preconditions?.hasV2() == true) {
            throw UnsupportedOperationException(
                "Trezor does not support Stellar V2 preconditions " +
                    "(ledger bounds, min sequence number/age/ledger gap, extra signers)"
            )
        }
    }

    private fun Memo.toTrezorMemo(): TrezorStellarMemo = when (this) {
        is MemoNone -> TrezorStellarMemo.None
        is MemoText -> {
            // MemoText holds raw bytes; getText() decodes them as UTF-8 (lossy). Signing the decoded
            // string would diverge from the caller's XDR for non-UTF-8 memo bytes, so require an exact
            // round-trip and fail loud otherwise.
            if (!bytes.contentEquals(text.toByteArray(Charsets.UTF_8))) {
                throw UnsupportedOperationException(
                    "Stellar memo text is not valid UTF-8 and cannot be faithfully signed by Trezor"
                )
            }
            TrezorStellarMemo.Text(text)
        }
        is MemoId -> TrezorStellarMemo.Id(id.toMemoIdLong())
        is MemoHash -> TrezorStellarMemo.Hash(bytes)
        is MemoReturnHash -> TrezorStellarMemo.ReturnHash(bytes)
        else -> throw UnsupportedOperationException(
            "Trezor does not support Stellar memo: ${javaClass.simpleName}"
        )
    }

    private fun Operation.toTrezorOperation(): TrezorStellarOperation = when (this) {
        is PaymentOperation -> TrezorStellarOperation.Payment(
            destination = destination,
            asset = asset.toTrezorAsset(),
            amount = amount.toStroops(),
            sourceAccount = sourceAccount
        )

        is CreateAccountOperation -> TrezorStellarOperation.CreateAccount(
            destination = destination,
            startingBalance = startingBalance.toStroops(),
            sourceAccount = sourceAccount
        )

        is ChangeTrustOperation -> TrezorStellarOperation.ChangeTrust(
            asset = asset.toTrezorAsset(),
            limit = limit.toStroops(),
            sourceAccount = sourceAccount
        )

        else -> throw UnsupportedOperationException(
            "Trezor does not support Stellar operation: ${javaClass.simpleName}"
        )
    }

    private fun ChangeTrustAsset.toTrezorAsset(): TrezorStellarAsset {
        val underlying = asset ?: throw UnsupportedOperationException(
            "Trezor does not support change-trust on liquidity pool shares"
        )
        return underlying.toTrezorAsset()
    }

    private fun Asset.toTrezorAsset(): TrezorStellarAsset = when (this) {
        is AssetTypeNative -> TrezorStellarAsset.Native
        is AssetTypeCreditAlphaNum4 -> TrezorStellarAsset.AlphaNum4(code, issuer)
        is AssetTypeCreditAlphaNum12 -> TrezorStellarAsset.AlphaNum12(code, issuer)
        else -> throw UnsupportedOperationException(
            "Trezor does not support Stellar asset: ${javaClass.simpleName}"
        )
    }

    /** Exact stroop conversion: throws on fractional precision or Long overflow, never truncates. */
    private fun BigDecimal.toStroops(): Long = movePointRight(STROOP_SCALE).longValueExact()

    private fun BigInteger?.toTimeboundLong(label: String): Long {
        val value = this ?: return 0L
        if (value < BigInteger.ZERO || value > MAX_UINT32) {
            throw UnsupportedOperationException("Stellar $label $value exceeds Trezor's uint32 range")
        }
        return value.toLong()
    }

    private fun BigInteger.toMemoIdLong(): Long {
        if (this < BigInteger.ZERO || this > MAX_LONG) {
            throw UnsupportedOperationException("Stellar memo id $this exceeds Trezor's supported range")
        }
        return toLong()
    }

    companion object {
        private const val STROOP_SCALE = 7
        private val MAX_UINT32 = BigInteger.valueOf(0xFFFFFFFFL)
        private val MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)
    }
}

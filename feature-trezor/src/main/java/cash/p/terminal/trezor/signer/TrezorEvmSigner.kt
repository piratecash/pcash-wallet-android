package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorEvmGasFee
import cash.p.terminal.trezorkit.client.TrezorEvmTx
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import io.horizontalsystems.ethereumkit.core.TransactionBuilder
import io.horizontalsystems.ethereumkit.core.TransactionSigner
import io.horizontalsystems.ethereumkit.core.signer.EthSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.core.toByteArray
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.ethereumkit.spv.rlp.RLP
import io.horizontalsystems.ethereumkit.spv.rlp.RLPList
import java.math.BigInteger

class TrezorEvmSigner(
    private val address: Address,
    private val chain: Chain,
    private val derivationPath: String,
    private val trezorClient: ITrezorClient
) : Signer(
    transactionBuilder = TransactionBuilder(address, chain.id),
    transactionSigner = TransactionSigner(MOCK_PRIVATE_KEY, chain.id),
    ethSigner = EthSigner(MOCK_PRIVATE_KEY, CryptoUtils, EIP712Encoder())
) {
    companion object {
        private val MOCK_PRIVATE_KEY = BigInteger.ONE

        private const val LEGACY_GAS_PRICE_INDEX = 1
        private const val LEGACY_GAS_LIMIT_INDEX = 2
        private const val EIP1559_MAX_PRIORITY_FEE_INDEX = 2
        private const val EIP1559_MAX_FEE_INDEX = 3
        private const val EIP1559_GAS_LIMIT_INDEX = 4
        private const val SIGNATURE_SIZE = 65
        private const val R_S_SIZE = 32
    }

    /**
     * Not supported for Trezor: the base [Signer] contract returns only a [Signature], but the
     * device signs over a full transaction and the caller needs the exact fields that were signed.
     * A bare signature broadcast against a mismatched raw transaction would recover a wrong sender.
     * Use [signTransaction], which returns the transaction the device actually signed.
     */
    override suspend fun signature(rawTransaction: RawTransaction): Signature =
        throw TrezorSigningException("Use signTransaction() for Trezor; signature() loses the device-signed fields")

    /**
     * Signs the transaction on the device over USB and returns both the signature and the transaction
     * the device actually signed. The device returns only v/r/s; the kit reassembles [serializedTx]
     * from the request fields, and we rebuild the raw transaction from it. Reconciliation is defense
     * in depth: it confirms the device signed exactly the fields we sent, so the signature can never
     * be broadcast against a mismatched transaction.
     */
    suspend fun signTransaction(rawTransaction: RawTransaction): SignedEvmTransaction {
        val deviceSignature = trezorClient.connect { signEthereum(rawTransaction.toTrezorEvmTx()) }
        val signature = Signature(
            v = deviceSignature.v,
            r = deviceSignature.r,
            s = deviceSignature.s
        )
        val serializedTx = deviceSignature.serializedTx
            ?: throw TrezorSigningException("Trezor did not return the signed transaction")
        val signedRawTransaction = reconcileSignedTransaction(rawTransaction, signature, serializedTx)
        verifySender(signedRawTransaction, signature)
        return SignedEvmTransaction(signature, signedRawTransaction)
    }

    private fun RawTransaction.toTrezorEvmTx(): TrezorEvmTx = TrezorEvmTx(
        addressN = TrezorDerivationPath.parse(derivationPath),
        nonce = nonce.toByteArray(),
        gasLimit = gasLimit.toByteArray(),
        to = to.hex,
        value = value.toTrimmedByteArray(),
        data = data,
        chainId = chain.id.toLong(),
        gasFee = when (val gp = gasPrice) {
            is GasPrice.Legacy -> TrezorEvmGasFee.Legacy(gasPrice = gp.legacyGasPrice.toByteArray())
            is GasPrice.Eip1559 -> TrezorEvmGasFee.Eip1559(
                maxFeePerGas = gp.maxFeePerGas.toByteArray(),
                maxPriorityFeePerGas = gp.maxPriorityFeePerGas.toByteArray()
            )
        }
    )

    /**
     * Minimal big-endian encoding Trezor expects, matching ethereumkit's `Long.toByteArray`: strip the
     * leading sign byte that [BigInteger.toByteArray] prepends for positive high-bit values, and encode
     * zero as an empty array — so the kit's RLP re-encoding matches `TransactionBuilder.encode` (which
     * encodes zero as an empty string), keeping reconciliation byte-equal for zero-value contract calls.
     */
    private fun BigInteger.toTrimmedByteArray(): ByteArray {
        val bytes = toByteArray()
        return if (bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun reconcileSignedTransaction(
        rawTransaction: RawTransaction,
        signature: Signature,
        serializedTx: ByteArray
    ): RawTransaction {
        val (signedGasPrice, signedGasLimit) = decodeSignedGas(serializedTx, rawTransaction.gasPrice)
        val signedRawTransaction = RawTransaction(
            gasPrice = signedGasPrice,
            gasLimit = signedGasLimit,
            to = rawTransaction.to,
            value = rawTransaction.value,
            nonce = rawTransaction.nonce,
            data = rawTransaction.data
        )
        if (!TransactionBuilder.encode(signedRawTransaction, signature, chain.id).contentEquals(serializedTx)) {
            throw TrezorSigningException("Rebuilt transaction does not match the device signature")
        }
        return signedRawTransaction
    }

    /**
     * Defense in depth on top of the byte-equality check: recovers the sender from the device
     * signature and confirms it matches our wallet address, so a signature from the wrong account
     * can never be broadcast.
     */
    private fun verifySender(signedRawTransaction: RawTransaction, signature: Signature) {
        val recovered = EvmSignatureRecovery.recoverSenderAddress(signedRawTransaction, signature, chain.id)
            ?: throw TrezorSigningException("Cannot recover sender from the device signature")
        if (recovered != address) {
            throw TrezorSigningException("Device signed with an unexpected account")
        }
    }

    private fun decodeSignedGas(serializedTx: ByteArray, requestedGasPrice: GasPrice): Pair<GasPrice, Long> {
        val (rlpPayload, isLegacy) = when (requestedGasPrice) {
            is GasPrice.Legacy -> serializedTx to true
            is GasPrice.Eip1559 -> serializedTx.copyOfRange(1, serializedTx.size) to false
        }
        val fields = RLP.decode2(rlpPayload).firstOrNull() as? RLPList
            ?: throw TrezorSigningException("Malformed serialized transaction")
        return if (isLegacy) {
            GasPrice.Legacy(fields.longAt(LEGACY_GAS_PRICE_INDEX)) to fields.longAt(LEGACY_GAS_LIMIT_INDEX)
        } else {
            val gasPrice = GasPrice.Eip1559(
                maxFeePerGas = fields.longAt(EIP1559_MAX_FEE_INDEX),
                maxPriorityFeePerGas = fields.longAt(EIP1559_MAX_PRIORITY_FEE_INDEX)
            )
            gasPrice to fields.longAt(EIP1559_GAS_LIMIT_INDEX)
        }
    }

    private fun RLPList.longAt(index: Int): Long =
        BigInteger(1, this[index].rlpData ?: ByteArray(0)).toLong()

    /**
     * Signs an EIP-191 `personal_sign` message on-device over USB (`signEthereumMessage`).
     * Returns `r‖s‖recId` (recId ∈ {0,1}), byte-identical to mnemonic signing, so callers can
     * treat hardware and software signatures uniformly.
     */
    suspend fun signPersonalMessage(message: ByteArray): ByteArray {
        val result = trezorClient.connect { signEthereumMessage(TrezorDerivationPath.parse(derivationPath), message) }
        val sig = result.signature
        if (sig.size != SIGNATURE_SIZE) {
            throw TrezorSigningException("Unexpected Trezor signature size: ${sig.size}")
        }
        val r = sig.copyOfRange(0, R_S_SIZE)
        val s = sig.copyOfRange(R_S_SIZE, 2 * R_S_SIZE)
        val hash = EvmSignatureRecovery.personalSignHash(message)
        val recId = resolveRecoveryId(hash, r, s)
            ?: throw TrezorSigningException("Device signed with an unexpected account")
        return r + s + byteArrayOf(recId.toByte())
    }

    /**
     * Determines which recovery id (0 or 1) recovers our wallet address. This doubles as the
     * defense-in-depth account check: a signature from the wrong account never resolves, and we
     * don't have to trust how Trezor happens to encode `v` in the raw signature byte.
     */
    private fun resolveRecoveryId(hash: ByteArray, r: ByteArray, s: ByteArray): Int? {
        val rInt = BigInteger(1, r)
        val sInt = BigInteger(1, s)
        return (0..1).firstOrNull { recId ->
            EvmSignatureRecovery.recoverMessageAddress(hash, rInt, sInt, recId) == address
        }
    }

    /** Not supported: Trezor has no raw-hash (`eth_sign`) operation for Ethereum. */
    suspend fun signLegacyHash(hash: ByteArray): ByteArray =
        throw TrezorSigningException("eth_sign (raw hash) is not supported on Trezor")

    /** Not supported: typed-data signing is not wired for Trezor. */
    suspend fun signTypedDataMessage(rawJson: String): ByteArray =
        throw TrezorSigningException("eth_signTypedData is not supported on Trezor")
}

data class SignedEvmTransaction(
    val signature: Signature,
    val rawTransaction: RawTransaction
)

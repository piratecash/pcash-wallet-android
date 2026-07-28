package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.runBlocking
import java.math.BigInteger

class TrezorTronSigner(
    private val expectedAddressBase58: String,
    private val derivationPath: String,
    private val trezorClient: ITrezorClient
) : Signer(BigInteger.ZERO) {

    companion object {
        private const val SIGNATURE_SIZE = 65
        private const val R_S_SIZE = 32
    }

    /**
     * tron-kit's [Signer.sign] is synchronous and called on its background send path, while the
     * USB signing API is suspend - so we bridge via [runBlocking] on that background thread (never
     * main). The kit's `connect` opens, initializes and closes the USB session for this one call.
     * Returns `r‖s‖recId` (recId ∈ 0..3), byte-identical to mnemonic signing.
     */
    override fun sign(createdTransaction: CreatedTransaction): ByteArray {
        val rawData = createdTransaction.raw_data_hex.hexStringToByteArray()
        val deviceSignature = runBlocking {
            trezorClient.connect { signTron(TrezorDerivationPath.parse(derivationPath), rawData) }
        }
        if (deviceSignature.size != SIGNATURE_SIZE) {
            throw TrezorSigningException("Unexpected Trezor signature size: ${deviceSignature.size}")
        }
        val r = deviceSignature.copyOfRange(0, R_S_SIZE)
        val s = deviceSignature.copyOfRange(R_S_SIZE, 2 * R_S_SIZE)
        val recId = resolveRecoveryId(Utils.sha256(rawData), r, s)
            ?: throw TrezorSigningException("Device signed with an unexpected account")
        return r + s + byteArrayOf(recId.toByte())
    }

    /**
     * Determines which recovery id (0..3) recovers our wallet address. This doubles as the
     * defense-in-depth account check: a signature from the wrong account never resolves, and we
     * don't have to trust how Trezor happens to encode `v` in the raw signature byte. A Tron
     * address is the same keccak-derived 20 bytes an EVM address is, just prefixed and
     * Base58Check-encoded - so EVM recovery applies as is.
     */
    private fun resolveRecoveryId(hash: ByteArray, r: ByteArray, s: ByteArray): Int? {
        val rInt = BigInteger(1, r)
        val sInt = BigInteger(1, s)
        return (0..3).firstOrNull { recId ->
            tronAddressFor(hash, rInt, sInt, recId) == expectedAddressBase58
        }
    }

    private fun tronAddressFor(hash: ByteArray, r: BigInteger, s: BigInteger, recId: Int): String? =
        EvmSignatureRecovery.recoverMessageAddress(hash, r, s, recId)
            ?.let { Address.fromRawWithoutPrefix(it.raw).base58 }
}

package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.RawData
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.Security

class TrezorTronSignerTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // signer exercises the real connect() path and we can stub/capture signTron.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private val pathSlot = slot<List<Int>>()
    private val rawDataSlot = slot<ByteArray>()

    // Deterministic key pair; the signer's expected address is derived from it, so a signature
    // produced with this key must resolve a recovery id. Lazy: crypto runs after setUp().
    private val privateKey = BigInteger("46".repeat(32), 16)
    private val expectedAddress by lazy { tronAddressOf(privateKey) }

    private val rawData = ByteArray(120) { it.toByte() }

    @Before
    fun setUp() {
        Security.addProvider(InternalBouncyCastleProvider.getInstance())
    }

    @Test
    fun sign_validDeviceSignature_returnsMnemonicFormatSignature() {
        val elliptic = ellipticSignature(privateKey)
        stubSignTron(deviceSignature(elliptic))

        val result = createSigner().sign(createdTransaction())

        // r‖s‖rawRecId - byte-identical to what mnemonic tron-kit signing produces.
        assertArrayEquals(elliptic, result)
        assertEquals(TrezorDerivationPath.parse("m/44'/195'/0'/0/0"), pathSlot.captured)
        assertArrayEquals(rawData, rawDataSlot.captured)
    }

    @Test
    fun sign_unrecognizedVByte_recoversRecIdFromSignature() {
        // The v byte the device returns is not trusted at all: even a garbage value must not
        // matter because the recovery id is brute-forced from r/s against the expected address.
        val elliptic = ellipticSignature(privateKey)
        stubSignTron(elliptic.sliceArray(0..63) + byteArrayOf(0x99.toByte()))

        val result = createSigner().sign(createdTransaction())

        assertArrayEquals(elliptic, result)
    }

    @Test
    fun sign_wrongAccountSignature_throws() {
        // The device signature is produced by a different private key than the account this
        // signer was constructed for, so the recovery-id resolution must find no match.
        val foreign = ellipticSignature(BigInteger("64".repeat(32), 16))
        stubSignTron(deviceSignature(foreign))

        assertThrows(TrezorSigningException::class.java) {
            createSigner().sign(createdTransaction())
        }
    }

    @Test
    fun sign_non65ByteSignature_throws() {
        stubSignTron(ByteArray(64))

        assertThrows(TrezorSigningException::class.java) {
            createSigner().sign(createdTransaction())
        }
    }

    @Test
    fun sign_corruptedSignature_throwsInsteadOfCrashing() {
        // r and s far above the curve order: every recovery candidate fails internally, which
        // must surface as a signing exception, not an uncaught crypto exception.
        stubSignTron(ByteArray(65) { 0xFF.toByte() })

        assertThrows(TrezorSigningException::class.java) {
            createSigner().sign(createdTransaction())
        }
    }

    @Test
    fun sign_allZeroSignature_throwsInsteadOfCrashing() {
        // r = 0 takes a different failure path than out-of-range values: it survives the range
        // checks and hits BigInteger.modInverse (ArithmeticException) inside recovery.
        stubSignTron(ByteArray(65))

        assertThrows(TrezorSigningException::class.java) {
            createSigner().sign(createdTransaction())
        }
    }

    private fun ellipticSignature(privateKey: BigInteger): ByteArray =
        CryptoUtils.ellipticSign(Utils.sha256(rawData), privateKey)

    /** Builds the 65-byte `r‖s‖v` payload Trezor returns for `TronSignTx` (v = 27 + recId). */
    private fun deviceSignature(ellipticSignature: ByteArray): ByteArray =
        ellipticSignature.sliceArray(0..63) + byteArrayOf((27 + ellipticSignature[64]).toByte())

    private fun stubSignTron(signature: ByteArray) {
        coEvery { session.signTron(capture(pathSlot), capture(rawDataSlot)) } returns signature
    }

    private fun createdTransaction() = CreatedTransaction(
        visible = false,
        txID = "",
        raw_data = RawData(emptyList(), "", "", 0, 0, null),
        raw_data_hex = rawData.joinToString("") { "%02x".format(it) },
        Error = null
    )

    private fun createSigner(address: String = expectedAddress) = TrezorTronSigner(
        expectedAddressBase58 = address,
        derivationPath = "m/44'/195'/0'/0/0",
        trezorClient = trezorClient
    )

    /** Derives the Tron base58 address (0x41-prefixed keccak-derived EVM bytes) for [privateKey]. */
    private fun tronAddressOf(privateKey: BigInteger): String {
        val publicKey = CryptoUtils.CURVE.g.multiply(privateKey).getEncoded(false)
        val evmAddressBytes = CryptoUtils.sha3(publicKey.copyOfRange(1, 65)).copyOfRange(12, 32)
        return Address.fromRawWithoutPrefix(evmAddressBytes).base58
    }
}

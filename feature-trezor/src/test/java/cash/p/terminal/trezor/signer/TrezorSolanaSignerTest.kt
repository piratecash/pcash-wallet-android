package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import com.solana.core.PublicKey
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TrezorSolanaSignerTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // signer exercises the real connect() path and we can stub/capture signSolana.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private val addressNSlot = slot<List<Int>>()
    private val serializedTxSlot = slot<ByteArray>()

    private val derivationPath = "m/44'/501'/0'/0'"
    private val serializedMessage = ByteArray(64) { (it + 1).toByte() }
    private val deviceSignature = ByteArray(64) { (it + 100).toByte() }

    private fun signer() = TrezorSolanaSigner(
        publicKey = PublicKey(ByteArray(32) { it.toByte() }),
        derivationPath = derivationPath,
        trezorClient = trezorClient
    )

    @Test
    fun sign_returnsDeviceSignature() {
        coEvery { session.signSolana(capture(addressNSlot), capture(serializedTxSlot)) } returns deviceSignature

        assertArrayEquals(deviceSignature, signer().sign(serializedMessage))
    }

    @Test
    fun sign_passesParsedDerivationPathAndMessage() {
        coEvery { session.signSolana(capture(addressNSlot), capture(serializedTxSlot)) } returns deviceSignature

        signer().sign(serializedMessage)

        assertEquals(TrezorDerivationPath.parse(derivationPath), addressNSlot.captured)
        assertArrayEquals(serializedMessage, serializedTxSlot.captured)
    }
}

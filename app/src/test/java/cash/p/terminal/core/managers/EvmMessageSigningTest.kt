package cash.p.terminal.core.managers

import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class EvmMessageSigningTest {

    @Before
    fun setUp() {
        installEthereumCryptoProviderForTest()
    }

    private fun mnemonicSigner(): Signer {
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
        val seed = AccountType.Mnemonic(words, "").seed
        return Signer.getInstance(seed, Chain.Ethereum)
    }

    @Test
    fun signPersonalMessage_mnemonicSigner_matchesSignByteArray() = runBlocking {
        val signer = mnemonicSigner()
        val message = "hello".toByteArray()

        val expected = signer.signByteArray(message)
        val actual = EvmMessageSigning.signPersonalMessage(signer, message)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun signLegacyHash_mnemonicSigner_matchesSignByteArrayLegacy() = runBlocking {
        val signer = mnemonicSigner()
        val hash = ByteArray(32) { it.toByte() }

        val expected = signer.signByteArrayLegacy(hash)
        val actual = EvmMessageSigning.signLegacyHash(signer, hash)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun signLegacyHash_trezorSignerNotSupported_propagatesException() {
        val signer = mockk<TrezorEvmSigner>()
        val hash = ByteArray(32)
        coEvery { signer.signLegacyHash(hash) } throws TrezorSigningException("eth_sign is not supported on Trezor")

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { EvmMessageSigning.signLegacyHash(signer, hash) }
        }
    }
}

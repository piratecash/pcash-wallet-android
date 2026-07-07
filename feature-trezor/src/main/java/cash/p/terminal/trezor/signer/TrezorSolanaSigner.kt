package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezorkit.client.ITrezorClient
import com.solana.core.Account
import com.solana.core.PublicKey
import kotlinx.coroutines.runBlocking

class TrezorSolanaSigner(
    override val publicKey: PublicKey,
    private val derivationPath: String,
    private val trezorClient: ITrezorClient
) : Account {

    override val supportsPriorityFees: Boolean get() = false

    /**
     * solana-kit's [Account.sign] is synchronous and called on its background send path, while the
     * USB signing API is suspend - so we bridge via [runBlocking] on that background thread (never
     * main). The kit's `connect` opens, initializes and closes the USB session for this one call.
     */
    override fun sign(serializedMessage: ByteArray): ByteArray = runBlocking {
        trezorClient.connect {
            signSolana(TrezorDerivationPath.parse(derivationPath), serializedMessage)
        }
    }
}

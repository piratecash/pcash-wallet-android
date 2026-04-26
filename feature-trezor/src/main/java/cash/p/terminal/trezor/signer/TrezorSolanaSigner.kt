package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.hexBytes
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.requirePayload
import com.solana.core.Account
import com.solana.core.PublicKey
import io.horizontalsystems.core.toRawHexString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TrezorSolanaSigner(
    override val publicKey: PublicKey,
    private val derivationPath: String,
    private val deepLinkManager: TrezorDeepLinkManager
) : Account {

    override val supportsPriorityFees: Boolean get() = false

    override fun sign(serializedMessage: ByteArray): ByteArray = runBlocking {
        val params = buildJsonObject {
            put("path", derivationPath)
            put("serializedTx", serializedMessage.toRawHexString())
        }
        val response = deepLinkManager.call(TrezorMethod.SolSignTransaction, params)
        response.requirePayload().hexBytes("signature")
    }
}

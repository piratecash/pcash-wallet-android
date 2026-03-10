package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.hexBytes
import cash.p.terminal.trezor.domain.hexInt
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.requirePayload
import io.horizontalsystems.ethereumkit.core.TransactionBuilder
import io.horizontalsystems.ethereumkit.core.TransactionSigner
import io.horizontalsystems.ethereumkit.core.signer.EthSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger

class TrezorEvmSigner(
    address: Address,
    private val chain: Chain,
    private val derivationPath: String,
    private val deepLinkManager: TrezorDeepLinkManager
) : Signer(
    transactionBuilder = TransactionBuilder(address, chain.id),
    transactionSigner = TransactionSigner(MOCK_PRIVATE_KEY, chain.id),
    ethSigner = EthSigner(MOCK_PRIVATE_KEY, CryptoUtils, EIP712Encoder())
) {
    companion object {
        private val MOCK_PRIVATE_KEY = BigInteger.ONE
    }

    override suspend fun signature(rawTransaction: RawTransaction): Signature {
        val params = buildJsonObject {
            put("path", derivationPath)
            put("transaction", buildTransactionJson(rawTransaction))
        }
        val response = deepLinkManager.call(TrezorMethod.EthSignTransaction, params)
        val payload = response.requirePayload()
        return Signature(
            v = payload.hexInt("v"),
            r = payload.hexBytes("r"),
            s = payload.hexBytes("s")
        )
    }

    private fun buildTransactionJson(rawTransaction: RawTransaction) = buildJsonObject {
        put("to", rawTransaction.to.hex)
        put("value", "0x" + rawTransaction.value.toString(16))
        put("gasLimit", "0x" + rawTransaction.gasLimit.toString(16))
        put("nonce", "0x" + rawTransaction.nonce.toString(16))
        put("data", rawTransaction.data.toHexString())
        put("chainId", chain.id)
        when (val gp = rawTransaction.gasPrice) {
            is GasPrice.Legacy -> {
                put("gasPrice", "0x" + gp.legacyGasPrice.toString(16))
            }
            is GasPrice.Eip1559 -> {
                put("maxFeePerGas", "0x" + gp.maxFeePerGas.toString(16))
                put("maxPriorityFeePerGas", "0x" + gp.maxPriorityFeePerGas.toString(16))
            }
        }
    }
}

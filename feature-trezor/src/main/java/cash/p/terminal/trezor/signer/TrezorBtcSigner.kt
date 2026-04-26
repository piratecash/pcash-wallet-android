package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.hexString
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.requirePayload
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.IFullTransactionSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.builder.SignedTransactionData
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TrezorBtcSigner(
    private val coin: String,
    private val derivationPath: String,
    private val deepLinkManager: TrezorDeepLinkManager
) : IInputSigner, ISchnorrInputSigner, IFullTransactionSigner {

    private var transactionSerializer: BaseTransactionSerializer? = null
    private var network: Network? = null

    override fun setTransactionSerializer(serializer: BaseTransactionSerializer) {
        this.transactionSerializer = serializer
    }

    override fun setNetwork(network: Network) {
        this.network = network
    }

    override suspend fun signFullTransaction(
        mutableTransaction: MutableTransaction
    ): SignedTransactionData {
        val tx = mutableTransaction.transaction
        val params = buildJsonObject {
            put("coin", coin)
            put("version", tx.version)
            put("locktime", tx.lockTime)
            put("inputs", buildInputsJson(mutableTransaction.inputsToSign))
            put("outputs", buildOutputsJson(mutableTransaction))
        }
        val response = deepLinkManager.call(TrezorMethod.BtcSignTransaction, params)
        val payload = response.requirePayload()
        return SignedTransactionData(
            serializedTx = payload.hexString("serializedTx"),
            signatures = emptyList()
        )
    }

    private fun buildInputsJson(inputs: List<InputToSign>) = buildJsonArray {
        inputs.forEach { input ->
            add(buildJsonObject {
                val publicKey = input.previousOutputPublicKey
                val changeSegment = if (publicKey.external) 0 else 1
                val fullPath = parseDerivationPath(derivationPath) + changeSegment + publicKey.index
                put("address_n", buildJsonArray { fullPath.forEach { add(JsonPrimitive(it)) } })
                put("prev_hash", input.input.previousOutputTxHash.toReversedHex())
                put("prev_index", input.input.previousOutputIndex)
                put("amount", input.previousOutput.value)
                put("sequence", input.input.sequence)
                put("script_type", inputScriptType(input.previousOutput.scriptType))
            })
        }
    }

    private fun buildOutputsJson(tx: MutableTransaction) = buildJsonArray {
        val changeAddress = tx.changeAddress?.stringValue
        val changeKey = tx.changePublicKey
        tx.outputs.forEach { output ->
            add(buildOutputJson(output, changeAddress, changeKey))
        }
    }

    private fun buildOutputJson(
        output: TransactionOutput,
        changeAddress: String?,
        changeKey: PublicKey?
    ) = buildJsonObject {
        when {
            output.scriptType == ScriptType.NULL_DATA -> {
                put("amount", 0)
                put("script_type", "PAYTOOPRETURN")
                // lockingScript = OP_RETURN + push_op + data; extract data after first 2 bytes
                put("op_return_data", output.lockingScript.drop(2).toByteArray().toHex())
            }
            changeAddress != null && changeKey != null && output.address == changeAddress -> {
                put("amount", output.value)
                val changeSegment = if (changeKey.external) 0 else 1
                val fullPath = parseDerivationPath(derivationPath) + changeSegment + changeKey.index
                put("address_n", buildJsonArray { fullPath.forEach { add(JsonPrimitive(it)) } })
                put("script_type", outputScriptType(output.scriptType))
            }
            else -> {
                put("amount", output.value)
                put("script_type", "PAYTOADDRESS")
                output.address?.let { put("address", it) }
            }
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun parseDerivationPath(path: String): List<Int> =
        path.split("/")
            .filter { it.isNotEmpty() && it != "m" }
            .map { segment ->
                val clean = segment.trimEnd('\'')
                val value = clean.toInt()
                if (segment.endsWith("'")) value or 0x80000000.toInt() else value
            }

    private fun inputScriptType(scriptType: ScriptType): String = when (scriptType) {
        ScriptType.P2PKH -> "SPENDADDRESS"
        ScriptType.P2SH -> "SPENDP2SHWITNESS"
        ScriptType.P2WPKH -> "SPENDWITNESS"
        ScriptType.P2WPKHSH -> "SPENDP2SHWITNESS"
        ScriptType.P2TR -> "SPENDTAPROOT"
        else -> "SPENDADDRESS"
    }

    private fun outputScriptType(scriptType: ScriptType): String = when (scriptType) {
        ScriptType.P2PKH -> "PAYTOADDRESS"
        ScriptType.P2SH -> "PAYTOP2SHWITNESS"
        ScriptType.P2WPKH -> "PAYTOWITNESS"
        ScriptType.P2WPKHSH -> "PAYTOP2SHWITNESS"
        ScriptType.P2TR -> "PAYTOTAPROOT"
        else -> "PAYTOADDRESS"
    }

    private fun ByteArray.toReversedHex(): String =
        reversed().joinToString("") { "%02x".format(it) }

    override suspend fun sigScriptEcdsaData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int
    ): List<ByteArray> = throw UnsupportedOperationException("Use signFullTransaction")

    override suspend fun sigScriptSchnorrData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int
    ): List<ByteArray> = throw UnsupportedOperationException("Use signFullTransaction")
}

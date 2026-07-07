package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorBtcInput
import cash.p.terminal.trezorkit.client.TrezorBtcOutput
import cash.p.terminal.trezorkit.client.TrezorBtcSignTx
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorOutputScriptType
import cash.p.terminal.trezorkit.client.TrezorPrevTx
import cash.p.terminal.trezorkit.client.TrezorPrevTxInput
import cash.p.terminal.trezorkit.client.TrezorPrevTxOutput
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.io.BitcoinInputMarkable
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.IFullTransactionSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.builder.SignedTransactionData
import io.horizontalsystems.bitcoincore.transactions.scripts.OP_RETURN
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType

class TrezorBtcSigner(
    private val coin: String,
    private val derivationPath: String,
    private val trezorClient: ITrezorClient
) : IInputSigner, ISchnorrInputSigner, IFullTransactionSigner {

    private var transactionSerializer: BaseTransactionSerializer? = null
    private var network: Network? = null
    private var previousTransactionProvider: BtcPreviousTransactionProvider? = null

    override fun setTransactionSerializer(serializer: BaseTransactionSerializer) {
        this.transactionSerializer = serializer
    }

    override fun setNetwork(network: Network) {
        this.network = network
    }

    fun setPreviousTransactionProvider(provider: BtcPreviousTransactionProvider) {
        this.previousTransactionProvider = provider
    }

    override suspend fun signFullTransaction(
        mutableTransaction: MutableTransaction
    ): SignedTransactionData {
        val tx = mutableTransaction.transaction
        val changeAddress = mutableTransaction.changeAddress?.stringValue
        val changeKey = mutableTransaction.changePublicKey
        val signTx = TrezorBtcSignTx(
            version = tx.version,
            lockTime = tx.lockTime,
            inputs = mutableTransaction.inputsToSign.map { it.toTrezorInput() },
            outputs = mutableTransaction.outputs.map { it.toTrezorOutput(changeAddress, changeKey) }
        )
        val prevTxByHash = collectPreviousTransactions(mutableTransaction.inputsToSign)

        val result = trezorClient.connect { signBitcoin(coin, signTx, prevTxByHash) }
        return SignedTransactionData(
            serializedTx = result.serializedTx.toHexString(),
            signatures = result.signatures.map { it.toHexString() }
        )
    }

    private fun InputToSign.toTrezorInput() = TrezorBtcInput(
        addressN = addressPath(previousOutputPublicKey),
        // Trezor expects prev_hash in display (big-endian, reversed) byte order - the firmware reverses
        // it back to internal when serializing. bitcoin-kit stores it internally, so reverse here.
        prevHash = input.previousOutputTxHash.reversedArray(),
        prevIndex = input.previousOutputIndex.toInt(),
        amount = previousOutput.value,
        scriptType = inputScriptType(previousOutput.scriptType),
        sequence = input.sequence
    )

    private fun TransactionOutput.toTrezorOutput(
        changeAddress: String?,
        changeKey: PublicKey?
    ): TrezorBtcOutput = when {
        scriptType == ScriptType.NULL_DATA -> TrezorBtcOutput.OpReturn(
            opReturnData = opReturnPayload(lockingScript)
        )

        changeAddress != null && changeKey != null && address == changeAddress -> TrezorBtcOutput.Change(
            addressN = addressPath(changeKey),
            amount = value,
            scriptType = outputScriptType(scriptType)
        )

        else -> TrezorBtcOutput.Address(
            // The firmware derives the locking script from the address, so PAYTOADDRESS covers every
            // external output regardless of its script type.
            address = requireNotNull(address) { "Trezor output is missing a recipient address" },
            amount = value,
            scriptType = TrezorOutputScriptType.PAYTOADDRESS
        )
    }

    private fun collectPreviousTransactions(inputs: List<InputToSign>): Map<String, TrezorPrevTx> {
        val serializer = requireNotNull(transactionSerializer) {
            "Transaction serializer is not set; cannot parse previous transactions for Trezor"
        }
        val provider = requireNotNull(previousTransactionProvider) {
            "Previous-transaction provider is not set for Trezor signing"
        }
        val prevTxByHash = mutableMapOf<String, TrezorPrevTx>()
        for (input in inputs) {
            // Display (reversed) txid: this is what getRawTransaction expects and also the hash the
            // device echoes when it requests the previous transaction (prev_hash is sent in display order).
            val displayTxid = input.input.previousOutputTxHash.toReversedHex()
            if (prevTxByHash.containsKey(displayTxid)) continue
            val rawHex = provider.getRawTransaction(displayTxid)
                ?: throw TrezorSigningException("Missing previous transaction $displayTxid required by Trezor")
            val fullTransaction = serializer.deserialize(BitcoinInputMarkable(rawHex.hexToByteArray()))
            prevTxByHash[displayTxid] = fullTransaction.toTrezorPrevTx()
        }
        return prevTxByHash
    }

    /**
     * Extracts the raw memo bytes from a NULL_DATA locking script. The kit builds a memo output as
     * `OP_RETURN + compactSize(len) + bytes` (BitcoinOutput.writeString), so the payload starts after
     * the compact-size varint, whose width grows for memos of 253+ bytes (reachable via multi-byte
     * UTF-8). Trezor rebuilds the push framing itself, so it needs only the raw bytes.
     */
    private fun opReturnPayload(lockingScript: ByteArray): ByteArray {
        require(lockingScript.size >= 2 && lockingScript[0] == OP_RETURN.toByte()) {
            "Unexpected OP_RETURN script for Trezor output"
        }
        val lengthPrefix = lockingScript[1].toInt() and 0xFF
        val dataStart = when {
            lengthPrefix < 0xFD -> 2
            lengthPrefix == 0xFD -> 4
            else -> throw UnsupportedOperationException(
                "Unsupported OP_RETURN length prefix $lengthPrefix for Trezor"
            )
        }
        return lockingScript.copyOfRange(dataStart, lockingScript.size)
    }

    private fun FullTransaction.toTrezorPrevTx() = TrezorPrevTx(
        version = header.version,
        lockTime = header.lockTime,
        inputs = inputs.map {
            TrezorPrevTxInput(
                // Same display (reversed) order Trezor expects for prev_hash everywhere.
                prevHash = it.previousOutputTxHash.reversedArray(),
                prevIndex = it.previousOutputIndex.toInt(),
                scriptSig = it.sigScript,
                sequence = it.sequence
            )
        },
        outputs = outputs.map {
            TrezorPrevTxOutput(amount = it.value, scriptPubkey = it.lockingScript)
        }
    )

    private fun addressPath(publicKey: PublicKey): List<Int> {
        val changeSegment = if (publicKey.external) 0 else 1
        return parseDerivationPath(derivationPath) + changeSegment + publicKey.index
    }

    private fun parseDerivationPath(path: String): List<Int> =
        path.split("/")
            .filter { it.isNotEmpty() && it != "m" }
            .map { segment ->
                val value = segment.trimEnd('\'').toInt()
                if (segment.endsWith("'")) value or HARDENED_BIT else value
            }

    private fun inputScriptType(scriptType: ScriptType): TrezorInputScriptType = when (scriptType) {
        ScriptType.P2PKH -> TrezorInputScriptType.SPENDADDRESS
        ScriptType.P2SH -> TrezorInputScriptType.SPENDP2SHWITNESS
        ScriptType.P2WPKH -> TrezorInputScriptType.SPENDWITNESS
        ScriptType.P2WPKHSH -> TrezorInputScriptType.SPENDP2SHWITNESS
        ScriptType.P2TR -> TrezorInputScriptType.SPENDTAPROOT
        else -> TrezorInputScriptType.SPENDADDRESS
    }

    private fun outputScriptType(scriptType: ScriptType): TrezorOutputScriptType = when (scriptType) {
        ScriptType.P2PKH -> TrezorOutputScriptType.PAYTOADDRESS
        ScriptType.P2SH -> TrezorOutputScriptType.PAYTOP2SHWITNESS
        ScriptType.P2WPKH -> TrezorOutputScriptType.PAYTOWITNESS
        ScriptType.P2WPKHSH -> TrezorOutputScriptType.PAYTOP2SHWITNESS
        ScriptType.P2TR -> TrezorOutputScriptType.PAYTOTAPROOT
        else -> TrezorOutputScriptType.PAYTOADDRESS
    }

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

    companion object {
        private val HARDENED_BIT = 0x80000000.toInt()
    }
}

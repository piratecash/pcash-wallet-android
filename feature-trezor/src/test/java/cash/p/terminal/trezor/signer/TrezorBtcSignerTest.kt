package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorBtcOutput
import cash.p.terminal.trezorkit.client.TrezorBtcSignResult
import cash.p.terminal.trezorkit.client.TrezorBtcSignTx
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorOutputScriptType
import cash.p.terminal.trezorkit.client.TrezorPrevTx
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.io.BitcoinOutput
import io.horizontalsystems.bitcoincore.models.Address
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionInput
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.OP_RETURN
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrezorBtcSignerTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // signer exercises the real connect() path and we can capture the arguments to signBitcoin.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private val coinSlot = slot<String>()
    private val signTxSlot = slot<TrezorBtcSignTx>()
    private val prevTxSlot = slot<Map<String, TrezorPrevTx>>()

    private val serializer = BaseTransactionSerializer()
    private val derivationPath = "m/84'/0'/0'"
    private val coin = "Bitcoin"

    private val deviceSerializedTx = ByteArray(10) { (it + 1).toByte() }
    private val deviceSignature = ByteArray(72) { (it + 50).toByte() }

    // A real previous transaction, round-tripped through the kit serializer so byte order and parsing
    // are exercised exactly as in production. Its hash is in internal (little-endian) byte order.
    private val prevTx = buildPreviousTransaction()
    private val prevTxHash = prevTx.header.hash
    private val prevTxRawHex = serializer.serialize(prevTx).toHexString()

    private val providerLookups = mutableListOf<String>()
    private val provider = object : BtcPreviousTransactionProvider {
        // Answers only for the display-order (reversed) txid, matching AbstractKit.getRawTransaction.
        override fun getRawTransaction(hash: String): String? {
            providerLookups += hash
            return prevTxRawHex.takeIf { hash == prevTxHash.toReversedHex() }
        }
    }

    @Before
    fun setUp() {
        coEvery {
            session.signBitcoin(capture(coinSlot), capture(signTxSlot), capture(prevTxSlot))
        } returns TrezorBtcSignResult(serializedTx = deviceSerializedTx, signatures = listOf(deviceSignature))
    }

    private fun signer() = TrezorBtcSigner(coin, derivationPath, trezorClient).apply {
        setTransactionSerializer(serializer)
        setPreviousTransactionProvider(provider)
    }

    @Test
    fun signFullTransaction_mapsInputsOutputsAndReturnsHexResult() {
        val input = inputToSign(prevTxHash, prevIndex = 1L, value = 60_000L, ScriptType.P2WPKH, external = true, index = 5)
        val changeKey = publicKey(external = false, index = 2)
        val mutableTransaction = mutableTransaction(
            inputs = listOf(input),
            outputs = listOf(
                addressOutput("bc1recipient", 40_000L),
                changeOutput("bc1change", 15_000L, ScriptType.P2WPKH),
                opReturnOutput("memo")
            ),
            changeAddress = "bc1change",
            changePublicKey = changeKey,
            version = 2,
            lockTime = 500L
        )

        val result = runBlocking { signer().signFullTransaction(mutableTransaction) }

        assertEquals(coin, coinSlot.captured)
        assertEquals(deviceSerializedTx.toHexString(), result.serializedTx)
        assertEquals(listOf(deviceSignature.toHexString()), result.signatures)

        val signTx = signTxSlot.captured
        assertEquals(2, signTx.version)
        assertEquals(500L, signTx.lockTime)

        val txInput = signTx.inputs.single()
        // Trezor expects prev_hash in display (reversed) byte order.
        assertArrayEquals(prevTxHash.reversedArray(), txInput.prevHash)
        assertEquals(1, txInput.prevIndex)
        assertEquals(60_000L, txInput.amount)
        assertEquals(TrezorInputScriptType.SPENDWITNESS, txInput.scriptType)
        assertEquals(listOf(hardened(84), hardened(0), hardened(0), 0, 5), txInput.addressN)

        val recipient = signTx.outputs[0] as TrezorBtcOutput.Address
        assertEquals("bc1recipient", recipient.address)
        assertEquals(40_000L, recipient.amount)
        assertEquals(TrezorOutputScriptType.PAYTOADDRESS, recipient.scriptType)

        val change = signTx.outputs[1] as TrezorBtcOutput.Change
        assertEquals(15_000L, change.amount)
        assertEquals(TrezorOutputScriptType.PAYTOWITNESS, change.scriptType)
        assertEquals(listOf(hardened(84), hardened(0), hardened(0), 1, 2), change.addressN)

        val opReturn = signTx.outputs[2] as TrezorBtcOutput.OpReturn
        assertArrayEquals("memo".toByteArray(), opReturn.opReturnData)
    }

    @Test
    fun signFullTransaction_longOpReturnMemo_extractsPayloadAfterMultiByteVarint() {
        val memo = "A".repeat(300) // 300 bytes >= 253 -> 3-byte compact-size length prefix
        val input = inputToSign(prevTxHash, prevIndex = 0L, value = 60_000L, ScriptType.P2WPKH, external = true, index = 0)
        val mutableTransaction = mutableTransaction(listOf(input), listOf(opReturnOutput(memo)))

        runBlocking { signer().signFullTransaction(mutableTransaction) }

        val opReturn = signTxSlot.captured.outputs.single() as TrezorBtcOutput.OpReturn
        assertArrayEquals(memo.toByteArray(), opReturn.opReturnData)
    }

    @Test
    fun signFullTransaction_prevTxKeyedAndLookedUpByDisplayTxid() {
        val input = inputToSign(prevTxHash, prevIndex = 0L, value = 60_000L, ScriptType.P2PKH, external = true, index = 0)
        val mutableTransaction = mutableTransaction(
            inputs = listOf(input),
            outputs = listOf(addressOutput("bc1recipient", 59_000L))
        )

        runBlocking { signer().signFullTransaction(mutableTransaction) }

        // Raw-tx lookup uses the display (reversed) txid, and the device echoes prev_hash in the same
        // display order, so the device-facing map is keyed by it too.
        assertEquals(listOf(prevTxHash.toReversedHex()), providerLookups)
        val prevMap = prevTxSlot.captured
        assertEquals(setOf(prevTxHash.toReversedHex()), prevMap.keys)

        val parsed = prevMap.getValue(prevTxHash.toReversedHex())
        assertEquals(prevTx.header.version, parsed.version)
        assertEquals(prevTx.header.lockTime, parsed.lockTime)
        assertEquals(prevTx.outputs.single().value, parsed.outputs.single().amount)
        assertArrayEquals(prevTx.outputs.single().lockingScript, parsed.outputs.single().scriptPubkey)
        // prev-tx input prev_hash is also in display (reversed) order.
        assertArrayEquals(prevTx.inputs.single().previousOutputTxHash.reversedArray(), parsed.inputs.single().prevHash)
        assertArrayEquals(prevTx.inputs.single().sigScript, parsed.inputs.single().scriptSig)
    }

    @Test
    fun signFullTransaction_reusesSinglePrevTxAcrossInputs() {
        val inputs = listOf(
            inputToSign(prevTxHash, prevIndex = 0L, value = 30_000L, ScriptType.P2PKH, external = true, index = 0),
            inputToSign(prevTxHash, prevIndex = 1L, value = 20_000L, ScriptType.P2PKH, external = true, index = 1)
        )
        val mutableTransaction = mutableTransaction(inputs, listOf(addressOutput("bc1recipient", 45_000L)))

        runBlocking { signer().signFullTransaction(mutableTransaction) }

        assertEquals(1, providerLookups.size)
        assertEquals(1, prevTxSlot.captured.size)
    }

    @Test
    fun signFullTransaction_mapsInputScriptTypes() {
        assertEquals(TrezorInputScriptType.SPENDADDRESS, inputScriptTypeFor(ScriptType.P2PKH))
        assertEquals(TrezorInputScriptType.SPENDP2SHWITNESS, inputScriptTypeFor(ScriptType.P2SH))
        assertEquals(TrezorInputScriptType.SPENDWITNESS, inputScriptTypeFor(ScriptType.P2WPKH))
        assertEquals(TrezorInputScriptType.SPENDP2SHWITNESS, inputScriptTypeFor(ScriptType.P2WPKHSH))
        assertEquals(TrezorInputScriptType.SPENDTAPROOT, inputScriptTypeFor(ScriptType.P2TR))
    }

    @Test
    fun signFullTransaction_missingPreviousTransaction_throws() {
        val unknownHash = ByteArray(32) { 0x11 }
        val input = inputToSign(unknownHash, prevIndex = 0L, value = 10_000L, ScriptType.P2PKH, external = true, index = 0)
        val mutableTransaction = mutableTransaction(listOf(input), listOf(addressOutput("bc1recipient", 9_000L)))

        var thrown: Throwable? = null
        try {
            runBlocking { signer().signFullTransaction(mutableTransaction) }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("Expected TrezorSigningException but got $thrown", thrown is TrezorSigningException)
    }

    private fun inputScriptTypeFor(scriptType: ScriptType): TrezorInputScriptType {
        val input = inputToSign(prevTxHash, prevIndex = 0L, value = 60_000L, scriptType, external = true, index = 0)
        val mutableTransaction = mutableTransaction(listOf(input), listOf(addressOutput("bc1recipient", 59_000L)))
        runBlocking { signer().signFullTransaction(mutableTransaction) }
        return signTxSlot.captured.inputs.single().scriptType
    }

    private fun buildPreviousTransaction(): FullTransaction {
        val header = Transaction(version = 1, lockTime = 0)
        val input = TransactionInput(
            previousOutputTxHash = ByteArray(32) { 7 },
            previousOutputIndex = 0L,
            sequence = 0xFFFFFFFFL
        ).apply { sigScript = byteArrayOf(0x47, 0x30, 0x44) }
        val p2pkhScript = byteArrayOf(0x76, 0xA9.toByte(), 0x14) + ByteArray(20) { 3 } + byteArrayOf(0x88.toByte(), 0xAC.toByte())
        val output = TransactionOutput(value = 60_000L, index = 0, script = p2pkhScript, type = ScriptType.P2PKH)
        return FullTransaction(header = header, inputs = listOf(input), outputs = listOf(output))
    }

    private fun inputToSign(
        prevHash: ByteArray,
        prevIndex: Long,
        value: Long,
        scriptType: ScriptType,
        external: Boolean,
        index: Int
    ): InputToSign {
        val txInput = TransactionInput(prevHash, prevIndex, sequence = 0xFFFFFFFFL)
        val prevOutput = TransactionOutput(value = value, index = 0, script = byteArrayOf(), type = scriptType)
        return InputToSign(txInput, prevOutput, publicKey(external, index))
    }

    private fun publicKey(external: Boolean, index: Int) = PublicKey().apply {
        this.external = external
        this.index = index
    }

    private fun addressOutput(address: String, value: Long) =
        TransactionOutput(value = value, index = 0, script = byteArrayOf(), type = ScriptType.P2WPKH, address = address)

    private fun changeOutput(address: String, value: Long, scriptType: ScriptType) =
        TransactionOutput(value = value, index = 1, script = byteArrayOf(), type = scriptType, address = address)

    private fun opReturnOutput(memo: String): TransactionOutput {
        // Build the locking script exactly as the kit does: OP_RETURN + compactSize(len) + utf8 bytes.
        val script = BitcoinOutput().writeByte(OP_RETURN).writeString(memo).toByteArray()
        return TransactionOutput(value = 0, index = 2, script = script, type = ScriptType.NULL_DATA)
    }

    private fun mutableTransaction(
        inputs: List<InputToSign>,
        outputs: List<TransactionOutput>,
        changeAddress: String? = null,
        changePublicKey: PublicKey? = null,
        version: Int = 2,
        lockTime: Long = 0L
    ): MutableTransaction {
        val mutableTransaction = MutableTransaction()
        mutableTransaction.transaction.version = version
        mutableTransaction.transaction.lockTime = lockTime
        inputs.forEach { mutableTransaction.inputsToSign.add(it) }
        mutableTransaction.outputs = outputs
        mutableTransaction.changeAddress = changeAddress?.let { addr ->
            mockk<Address> { every { stringValue } returns addr }
        }
        mutableTransaction.changePublicKey = changePublicKey
        return mutableTransaction
    }

    private fun hardened(index: Int): Int = index or 0x80000000.toInt()
}

package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorStellarAsset
import cash.p.terminal.trezorkit.client.TrezorStellarMemo
import cash.p.terminal.trezorkit.client.TrezorStellarOperation
import cash.p.terminal.trezorkit.client.TrezorStellarSignTx
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.stellar.sdk.Account
import org.stellar.sdk.Asset
import org.stellar.sdk.ChangeTrustAsset
import org.stellar.sdk.KeyPair
import org.stellar.sdk.LedgerBounds
import org.stellar.sdk.LiquidityPool
import org.stellar.sdk.Memo
import org.stellar.sdk.MemoHash
import org.stellar.sdk.MemoId
import org.stellar.sdk.MemoReturnHash
import org.stellar.sdk.MemoText
import org.stellar.sdk.Network
import org.stellar.sdk.TimeBounds
import org.stellar.sdk.Transaction
import org.stellar.sdk.TransactionBuilder
import org.stellar.sdk.TransactionPreconditions
import org.stellar.sdk.operations.ChangeTrustOperation
import org.stellar.sdk.operations.CreateAccountOperation
import org.stellar.sdk.operations.Operation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.operations.RestoreFootprintOperation
import java.math.BigDecimal
import java.math.BigInteger

class TrezorStellarSignerTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // signer exercises the real connect() path and we can capture the TrezorStellarSignTx it builds.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private val signTxSlot = slot<TrezorStellarSignTx>()

    private val derivationPath = "m/44'/148'/0'"
    private val networkPassphrase = Network.PUBLIC.networkPassphrase
    private val signerPublicKey = ByteArray(32) { it.toByte() }
    private val deviceSignature = ByteArray(64) { (it + 100).toByte() }

    private val sourceKeyPair = KeyPair.random()
    private val issuer = KeyPair.random().accountId
    private val minTime = 1_600_000_000L
    private val maxTime = 1_700_000_000L

    @Before
    fun setUp() {
        coEvery { session.signStellar(capture(signTxSlot)) } returns deviceSignature
    }

    private fun signer(publicKey: ByteArray = signerPublicKey) = TrezorStellarSigner(
        publicKey = publicKey,
        derivationPath = derivationPath,
        networkPassphrase = networkPassphrase,
        trezorClient = trezorClient
    )

    @Test
    fun signTransaction_paymentAndChangeTrust_mapsToTrezorSignTx() {
        val usdc = ChangeTrustAsset(Asset.create("USDC:$issuer"))
        val transaction = buildTransaction(
            operations = listOf(
                payment(Asset.createNativeAsset(), BigDecimal("1.5")),
                ChangeTrustOperation.builder().asset(usdc).limit(BigDecimal("100")).build()
            )
        )

        val signTx = signAndCapture(transaction)

        assertEquals(TrezorDerivationPath.parse(derivationPath), signTx.addressN)
        assertEquals(networkPassphrase, signTx.networkPassphrase)
        assertEquals(transaction.sourceAccount, signTx.source)
        assertEquals(transaction.fee.toInt(), signTx.fee)
        assertEquals(transaction.sequenceNumber, signTx.sequenceNumber)
        assertEquals(minTime, signTx.timeboundsStart)
        assertEquals(maxTime, signTx.timeboundsEnd)

        val paymentOp = signTx.operations[0] as TrezorStellarOperation.Payment
        assertEquals(TrezorStellarAsset.Native, paymentOp.asset)
        assertEquals(15_000_000L, paymentOp.amount)

        val changeTrustOp = signTx.operations[1] as TrezorStellarOperation.ChangeTrust
        assertEquals(TrezorStellarAsset.AlphaNum4("USDC", issuer), changeTrustOp.asset)
        assertEquals(1_000_000_000L, changeTrustOp.limit)
    }

    @Test
    fun signTransaction_createAccount_mapsStartingBalanceToStroops() {
        val destination = KeyPair.random().accountId
        val transaction = buildTransaction(
            operations = listOf(
                CreateAccountOperation.builder().destination(destination).startingBalance(BigDecimal("2")).build()
            )
        )

        val op = signAndCapture(transaction).operations.single() as TrezorStellarOperation.CreateAccount

        assertEquals(destination, op.destination)
        assertEquals(20_000_000L, op.startingBalance)
    }

    @Test
    fun signTransaction_alphaNum12Asset_mapsToAlphaNum12() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.create("USDCOIN123:$issuer"), BigDecimal("1")))
        )

        val op = signAndCapture(transaction).operations.single() as TrezorStellarOperation.Payment

        assertEquals(TrezorStellarAsset.AlphaNum12("USDCOIN123", issuer), op.asset)
    }

    @Test
    fun signTransaction_memoText_mapsText() {
        assertEquals(TrezorStellarMemo.Text("hello"), memoOf(MemoText("hello")))
    }

    @Test
    fun signTransaction_memoId_mapsId() {
        assertEquals(TrezorStellarMemo.Id(42L), memoOf(MemoId(42L)))
    }

    @Test
    fun signTransaction_nonUtf8MemoText_throws() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            memo = MemoText(byteArrayOf(0xFF.toByte()))
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_memoHash_mapsHash() {
        val hash = ByteArray(32) { 1 }
        val memo = memoOf(MemoHash(hash)) as TrezorStellarMemo.Hash
        assertArrayEquals(hash, memo.hash)
    }

    @Test
    fun signTransaction_memoReturnHash_mapsReturnHash() {
        val hash = ByteArray(32) { 2 }
        val memo = memoOf(MemoReturnHash(hash)) as TrezorStellarMemo.ReturnHash
        assertArrayEquals(hash, memo.hash)
    }

    @Test
    fun signTransaction_noTimebounds_defaultsToZero() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            preconditions = null
        )

        val signTx = signAndCapture(transaction)

        assertEquals(0L, signTx.timeboundsStart)
        assertEquals(0L, signTx.timeboundsEnd)
    }

    @Test
    fun signTransaction_maxTrustlineLimit_convertsExactlyToLongMax() {
        val transaction = buildTransaction(
            operations = listOf(changeTrust(BigDecimal("922337203685.4775807")))
        )

        val op = signAndCapture(transaction).operations.single() as TrezorStellarOperation.ChangeTrust

        assertEquals(Long.MAX_VALUE, op.limit)
    }

    @Test
    fun signTransaction_limitOverflowsLong_throws() {
        val transaction = buildTransaction(
            operations = listOf(changeTrust(BigDecimal("922337203685.4775808")))
        )

        assertSigningThrows<ArithmeticException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_ledgerBoundsPrecondition_throws() {
        assertPreconditionRejected { it.ledgerBounds(LedgerBounds(1, 2)) }
    }

    @Test
    fun signTransaction_minSeqNumberPrecondition_throws() {
        assertPreconditionRejected { it.minSeqNumber(5L) }
    }

    @Test
    fun signTransaction_minSeqAgePrecondition_throws() {
        assertPreconditionRejected { it.minSeqAge(BigInteger.ONE) }
    }

    @Test
    fun signTransaction_minSeqLedgerGapPrecondition_throws() {
        assertPreconditionRejected { it.minSeqLedgerGap(1L) }
    }

    @Test
    fun signTransaction_extraSignersPrecondition_throws() {
        val extraSigner = KeyPair.random().xdrSignerKey
        assertPreconditionRejected { it.extraSigners(listOf(extraSigner)) }
    }

    @Test
    fun signTransaction_sorobanTransaction_throws() {
        val transaction = buildTransaction(
            operations = listOf(RestoreFootprintOperation.builder().build())
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_memoIdOverflowsLong_throws() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            memo = MemoId(BigInteger("18446744073709551615")) // 2^64 - 1, above Long.MAX
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_timeboundsOutOfUint32Range_throws() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            preconditions = TransactionPreconditions.builder()
                .timeBounds(TimeBounds(BigInteger.ZERO, BigInteger.valueOf(0x1_0000_0000L))) // 2^32
                .build()
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_poolShareChangeTrust_throws() {
        val poolShare = ChangeTrustAsset(
            LiquidityPool(Asset.createNativeAsset(), Asset.create("USDC:$issuer"))
        )
        val transaction = buildTransaction(
            operations = listOf(ChangeTrustOperation.builder().asset(poolShare).limit(BigDecimal("1")).build())
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    @Test
    fun signTransaction_buildsDecoratedSignatureFromDeviceBytes() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1")))
        )

        val decorated = runBlocking { signer().signTransaction(transaction) }

        assertArrayEquals(signerPublicKey.copyOfRange(28, 32), decorated.hint.signatureHint)
        assertArrayEquals(deviceSignature, decorated.signature.signature)
    }

    @Test
    fun sign_afterPrepareTransaction_signsPendingTransaction() {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1")))
        )
        val signerInstance = signer()

        signerInstance.prepareTransaction(transaction)
        runBlocking { signerInstance.sign(ByteArray(32)) }

        assertEquals(transaction.sourceAccount, signTxSlot.captured.source)
    }

    @Test
    fun sign_withoutPrepareTransaction_throws() {
        assertSigningThrows<IllegalArgumentException> { signer().sign(ByteArray(32)) }
    }

    private fun payment(asset: Asset, amount: BigDecimal): PaymentOperation =
        PaymentOperation.builder()
            .destination(KeyPair.random().accountId)
            .asset(asset)
            .amount(amount)
            .build()

    private fun changeTrust(limit: BigDecimal): ChangeTrustOperation =
        ChangeTrustOperation.builder()
            .asset(ChangeTrustAsset(Asset.create("USDC:$issuer")))
            .limit(limit)
            .build()

    private fun buildTransaction(
        operations: List<Operation>,
        memo: Memo? = null,
        preconditions: TransactionPreconditions? = defaultPreconditions()
    ): Transaction {
        val builder = TransactionBuilder(Account(sourceKeyPair.accountId, 4L), Network.PUBLIC).setBaseFee(100)
        operations.forEach { builder.addOperation(it) }
        memo?.let { builder.addMemo(it) }
        if (preconditions != null) {
            builder.addPreconditions(preconditions)
        } else {
            builder.setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
        }
        return builder.build()
    }

    private fun defaultPreconditions(): TransactionPreconditions =
        TransactionPreconditions.builder().timeBounds(TimeBounds(minTime, maxTime)).build()

    private fun signAndCapture(transaction: Transaction): TrezorStellarSignTx {
        runBlocking { signer().signTransaction(transaction) }
        return signTxSlot.captured
    }

    private fun memoOf(memo: Memo): TrezorStellarMemo {
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            memo = memo
        )
        return signAndCapture(transaction).memo
    }

    private fun assertPreconditionRejected(
        addV2Field: (TransactionPreconditions.TransactionPreconditionsBuilder) -> Unit
    ) {
        val builder = TransactionPreconditions.builder().timeBounds(TimeBounds(minTime, maxTime))
        addV2Field(builder)
        val transaction = buildTransaction(
            operations = listOf(payment(Asset.createNativeAsset(), BigDecimal("1"))),
            preconditions = builder.build()
        )

        assertSigningThrows<UnsupportedOperationException> { signer().signTransaction(transaction) }
    }

    private inline fun <reified T : Throwable> assertSigningThrows(noinline block: suspend () -> Unit) {
        var thrown: Throwable? = null
        try {
            runBlocking { block() }
        } catch (e: Throwable) {
            thrown = e
        }
        val actual = requireNotNull(thrown) { "Expected ${T::class.simpleName} to be thrown, but nothing was" }
        assertTrue(
            "Expected ${T::class.simpleName} but got ${actual::class.simpleName}: ${actual.message}",
            actual is T
        )
    }
}

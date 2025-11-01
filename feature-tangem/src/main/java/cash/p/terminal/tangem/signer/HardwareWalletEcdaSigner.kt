package cash.p.terminal.tangem.signer

import cash.p.terminal.tangem.domain.canonicalise
import cash.p.terminal.tangem.domain.usecase.SignHashesTransactionUseCase
import cash.p.terminal.tangem.domain.usecase.SignMultipleHashesUseCase
import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.sign.SignData
import com.tangem.operations.sign.SignResponse
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.IEcdsaInputBatchSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.model.DataToSign
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoincore.utils.Utils
import io.horizontalsystems.hdwalletkit.ECDSASignature
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.math.BigInteger

class HardwareWalletEcdaSigner(
    private val hardwarePublicKey: HardwarePublicKey
) : IInputSigner, IEcdsaInputBatchSigner {

    private val signHashesTransactionUseCase: SignHashesTransactionUseCase by inject(
        SignHashesTransactionUseCase::class.java
    )

    private val signMultipleHashesUseCase: SignMultipleHashesUseCase by inject(
        SignMultipleHashesUseCase::class.java
    )

    private var transactionSerializer: BaseTransactionSerializer? = null
    private var network: Network? = null

    override fun setTransactionSerializer(serializer: BaseTransactionSerializer) {
        this.transactionSerializer = serializer
    }

    override fun setNetwork(network: Network) {
        this.network = network
    }

    override suspend fun sigScriptEcdsaData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int
    ): List<ByteArray> {
        val transactionSerializer =
            requireNotNull(transactionSerializer) { "Transaction serializer must be set before signing" }
        val network = requireNotNull(network) { "Network must be set before signing" }

        val input = inputsToSign[index]
        val prevOutput = input.previousOutput
        val publicKey = input.previousOutputPublicKey

        val txContent = transactionSerializer.serializeForSignature(
            transaction = transaction,
            inputsToSign = inputsToSign,
            outputs = outputs,
            inputIndex = index,
            isWitness = prevOutput.scriptType.isWitness || network.sigHashForked
        ) + byteArrayOf(network.sigHashValue, 0, 0, 0)

        val hashToSign = Utils.doubleDigest(txContent)
        val changeSegment = if (publicKey.external) "0" else "1"
        val addressIndexSegment = publicKey.index.toString()
        val fullDerivationPathString =
            "${hardwarePublicKey.derivationPath}/$changeSegment/$addressIndexSegment"
        Timber.tag("HardwareWalletSigner").d("sigScriptEcdsaData $fullDerivationPathString")
        val signResponse: CompletionResult<SignResponse> = signHashesTransactionUseCase(
            hashes = arrayOf(hashToSign),
            walletPublicKey = hardwarePublicKey.publicKey,
            derivationPath = DerivationPath(fullDerivationPathString)
        )
        when (signResponse) {
            is CompletionResult.Success -> {
                val rawSignatureFromTangem = signResponse.data.signatures.firstOrNull()
                    ?: throw Error("No signature returned from signing operation")
                val rBytes = rawSignatureFromTangem.copyOfRange(0, 32)
                val sBytes = rawSignatureFromTangem.copyOfRange(32, 64)
                val r = BigInteger(1, rBytes)
                var s = BigInteger(1, sBytes)
                val derSignatureFromTangem = ECDSASignature(r, s).canonicalise().encodeToDER()
                val finalSignature = derSignatureFromTangem + network.sigHashValue
                return when (prevOutput.scriptType) {
                    ScriptType.P2PK -> listOf(finalSignature)
                    else -> listOf(finalSignature, publicKey.publicKey)
                }
            }

            is CompletionResult.Failure -> throw signResponse.error
        }
    }

    override suspend fun prepareDataForEcdsaSigning(mutableTransaction: MutableTransaction): List<DataToSign> {
        Timber.tag("HardwareWalletSigner")
            .d("prepareDataForEcdsaSigning ${mutableTransaction.inputsToSign.size}")
        val transactionSerializer =
            requireNotNull(transactionSerializer) { "Transaction serializer must be set before signing" }
        val network = requireNotNull(network) { "Network must be set before signing" }

        return buildList {
            mutableTransaction.inputsToSign.forEachIndexed { index, input ->
                val prevOutput = input.previousOutput
                val publicKey = input.previousOutputPublicKey

                val txContent = transactionSerializer.serializeForSignature(
                    transaction = mutableTransaction.transaction,
                    inputsToSign = mutableTransaction.inputsToSign,
                    outputs = mutableTransaction.outputs,
                    inputIndex = index,
                    isWitness = prevOutput.scriptType.isWitness || network.sigHashForked
                ) + byteArrayOf(network.sigHashValue, 0, 0, 0)

                add(
                    DataToSign(
                        publicKey = publicKey,
                        scriptType = prevOutput.scriptType,
                        data = Utils.doubleDigest(txContent)
                    )
                )
            }
        }
    }

    override suspend fun sigScriptEcdsaData(data: List<DataToSign>): List<List<ByteArray>> {
        Timber.tag("HardwareWalletSigner")
            .d("sigScriptEcdsaData with MultipleSignCommand: ${data.size}")
        val network = requireNotNull(network) { "Network must be set before signing" }

        if (data.isEmpty()) {
            Timber.tag("HardwareWalletEcdaSigner").w("No data to sign")
            return emptyList()
        }

        // Prepare SignData for each input with its derivation path
        val signDataList = data.map { dataToSign ->
            val publicKey = dataToSign.publicKey
            val changeSegment = if (publicKey.external) "0" else "1"
            val addressIndexSegment = publicKey.index.toString()
            val fullDerivationPathString =
                "${hardwarePublicKey.derivationPath}/$changeSegment/$addressIndexSegment"

            SignData(
                derivationPath = DerivationPath(fullDerivationPathString),
                hash = dataToSign.data,
                publicKey = publicKey.publicKey
            )
        }

        // Sign all hashes with different derivation paths in a single card tap
        val signResponse = signMultipleHashesUseCase(
            dataToSign = signDataList,
            walletPublicKey = hardwarePublicKey.publicKey
        )

        return when (signResponse) {
            is CompletionResult.Success -> {
                val responses = signResponse.data.asReversed()

                require(responses.size == data.size) {
                    "Unexpected signatures count: ${responses.size} != ${data.size}"
                }

                data.zip(responses).map { (dataToSign, response) ->
                    val rawSignature = response.signature
                    val rBytes = rawSignature.copyOfRange(0, 32)
                    val sBytes = rawSignature.copyOfRange(32, 64)
                    val r = BigInteger(1, rBytes)
                    val s = BigInteger(1, sBytes)
                    val derSignature = ECDSASignature(r, s).canonicalise().encodeToDER()
                    val finalSignature = derSignature + network.sigHashValue

                    when (dataToSign.scriptType) {
                        ScriptType.P2PK -> listOf(finalSignature)
                        else -> listOf(finalSignature, dataToSign.publicKey.publicKey)
                    }
                }
            }

            is CompletionResult.Failure -> throw signResponse.error
        }
    }
}

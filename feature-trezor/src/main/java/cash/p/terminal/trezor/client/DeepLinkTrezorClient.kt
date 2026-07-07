package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorBtcSignResult
import cash.p.terminal.trezorkit.client.TrezorBtcSignTx
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorEvmTx
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPrevTx
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.trezorkit.client.TrezorSignature
import cash.p.terminal.trezorkit.client.TrezorStellarSignTx
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [ITrezorClient] backed by the legacy Trezor Suite deeplink (Connect JSON API). It is the
 * fall-back leg of the USB migration switcher: it keeps the app working on the deeplink path
 * while the USB client is verified, and is removed once the cutover to USB is complete.
 *
 * Only read operations are wired; deeplink signing still goes through the existing signers, so the
 * sign methods here are never reached and throw if they somehow are.
 */
internal class DeepLinkTrezorClient(
    private val deepLinkManager: TrezorDeepLinkManager,
) : ITrezorClient, TrezorClientSession {

    // Each deeplink call is independent - there is no persistent session to open or close.
    override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = block()

    override suspend fun getFeatures(): TrezorFeatures {
        val response = deepLinkManager.call(TrezorMethod.GetFeatures)
        check(response.success) { "Trezor getFeatures failed: ${response.error}" }
        val payload = requireNotNull(response.payload) { "Trezor getFeatures returned no payload" }.jsonObject
        val major = payload["major_version"]?.jsonPrimitive?.content ?: "0"
        val minor = payload["minor_version"]?.jsonPrimitive?.content ?: "0"
        val patch = payload["patch_version"]?.jsonPrimitive?.content ?: "0"
        return TrezorFeatures(
            deviceId = payload["device_id"]?.jsonPrimitive?.content,
            model = payload["model"]?.jsonPrimitive?.content,
            internalModel = payload["internal_model"]?.jsonPrimitive?.content,
            firmwareVersion = "$major.$minor.$patch",
            passphraseProtection = payload["passphrase_protection"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    override suspend fun getPublicKeys(requests: List<TrezorPublicKeyRequest>): List<TrezorKeyResult> {
        val specs = requests.map { it.toConnectSpec() }
        val results = arrayOfNulls<TrezorKeyResult>(requests.size)
        // Group by Connect method so a whole coin family can be fetched in a single Suite round-trip.
        specs.withIndex()
            .groupBy { it.value.method }
            .forEach { (method, indexed) ->
                if (indexed.size == 1) {
                    val (index, spec) = indexed.single()
                    results[index] = fetchSingle(spec)
                } else {
                    val bundle = fetchBundle(method, indexed.map { it.value })
                    indexed.forEachIndexed { position, (index, _) -> results[index] = bundle[position] }
                }
            }
        return results.mapIndexed { index, result ->
            result ?: error("Trezor returned no key for request #$index (${requests[index]})")
        }
    }

    override suspend fun signEthereum(tx: TrezorEvmTx): TrezorSignature = signUnsupported()

    override suspend fun signBitcoin(
        coinName: String,
        tx: TrezorBtcSignTx,
        prevTxByHash: Map<String, TrezorPrevTx>,
    ): TrezorBtcSignResult = signUnsupported()

    override suspend fun signSolana(addressN: List<Int>, serializedTx: ByteArray): ByteArray = signUnsupported()

    override suspend fun signStellar(tx: TrezorStellarSignTx): ByteArray = signUnsupported()

    private fun signUnsupported(): Nothing =
        throw UnsupportedOperationException("Deeplink signing goes through the legacy signers, not ITrezorClient")

    private suspend fun fetchSingle(spec: ConnectKeySpec): TrezorKeyResult? {
        val params = JsonObject(
            buildMap {
                spec.coin?.let { put("coin", JsonPrimitive(it)) }
                put("path", JsonPrimitive(spec.path))
            }
        )
        val response = deepLinkManager.call(spec.method, params)
        if (!response.success) return null
        return parseKeyResult(response.payload?.jsonObject, spec.keyField)
    }

    private suspend fun fetchBundle(method: TrezorMethod, specs: List<ConnectKeySpec>): List<TrezorKeyResult?> {
        val bundleItems = specs.map { spec ->
            JsonObject(
                buildMap {
                    spec.coin?.let { put("coin", JsonPrimitive(it)) }
                    put("path", JsonPrimitive(spec.path))
                    put("showOnTrezor", JsonPrimitive(false))
                }
            )
        }
        val params = JsonObject(mapOf("bundle" to JsonArray(bundleItems)))
        val response = deepLinkManager.call(method, params)
        if (!response.success) return specs.map { null }
        val payloadArray = response.payload?.jsonArray ?: return specs.map { null }
        return specs.mapIndexed { i, spec -> parseKeyResult(payloadArray.getOrNull(i)?.jsonObject, spec.keyField) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseKeyResult(payload: JsonObject?, keyField: String): TrezorKeyResult? {
        val keyValue = payload?.get(keyField)?.jsonPrimitive?.content ?: return null
        val publicKeyHex = payload["publicKey"]?.jsonPrimitive?.content.orEmpty()
        val chainCodeHex = payload["chainCode"]?.jsonPrimitive?.content.orEmpty()
        return TrezorKeyResult(
            key = keyValue,
            publicKey = if (publicKeyHex.isEmpty()) ByteArray(0) else publicKeyHex.hexToByteArray(),
            chainCode = if (chainCodeHex.isEmpty()) ByteArray(0) else chainCodeHex.hexToByteArray(),
        )
    }

    private fun TrezorPublicKeyRequest.toConnectSpec(): ConnectKeySpec = when (this) {
        is TrezorPublicKeyRequest.Bitcoin ->
            ConnectKeySpec(TrezorMethod.BtcGetPublicKey, coinName.toConnectCoin(), TrezorDerivationPath.format(path), "xpub")
        is TrezorPublicKeyRequest.Ethereum ->
            ConnectKeySpec(TrezorMethod.EthGetPublicKey, null, TrezorDerivationPath.format(path), "xpub")
        is TrezorPublicKeyRequest.Solana ->
            ConnectKeySpec(TrezorMethod.SolGetPublicKey, null, TrezorDerivationPath.format(path), "publicKey")
        is TrezorPublicKeyRequest.Stellar ->
            ConnectKeySpec(TrezorMethod.XlmGetAddress, null, TrezorDerivationPath.format(path), "address")
    }

    // Bitcoin-family coins are addressed by full name over USB but by Connect shortcut over deeplink.
    private fun String.toConnectCoin(): String = when (this) {
        "Bitcoin" -> "btc"
        "Litecoin" -> "ltc"
        "Bcash" -> "bch"
        "Dash" -> "dash"
        "Dogecoin" -> "doge"
        "Zcash" -> "zec"
        else -> lowercase()
    }

    private data class ConnectKeySpec(
        val method: TrezorMethod,
        val coin: String?,
        val path: String,
        val keyField: String,
    )
}

package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.model.TrezorResponse
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkTrezorClientTest {

    private val manager = mockk<TrezorDeepLinkManager>()
    private val client = DeepLinkTrezorClient(manager)

    @Test
    fun getFeatures_parsesIdModelAndAssemblesVersion() = runBlocking {
        coEvery { manager.call(TrezorMethod.GetFeatures, any()) } returns TrezorResponse(
            success = true,
            payload = buildJsonObject {
                put("device_id", "DEV123")
                put("model", "T")
                put("internal_model", "T3W1")
                put("major_version", "2")
                put("minor_version", "8")
                put("patch_version", "7")
                put("passphrase_protection", true)
            }
        )

        val features = client.connect { getFeatures() }

        assertEquals("DEV123", features.deviceId)
        assertEquals("T3W1", features.internalModel)
        assertEquals("2.8.7", features.firmwareVersion)
        assertEquals(true, features.passphraseProtection)
    }

    @Test
    fun getPublicKeys_singleEthereum_parsesXpubAndNode() = runBlocking {
        coEvery { manager.call(TrezorMethod.EthGetPublicKey, any()) } returns TrezorResponse(
            success = true,
            payload = buildJsonObject {
                put("xpub", "xpubETH")
                put("publicKey", "0a0b")
                put("chainCode", "0c0d")
            }
        )

        val result = client.connect {
            getPublicKeys(listOf(TrezorPublicKeyRequest.Ethereum(TrezorDerivationPath.parse("m/44'/60'/0'/0/0"))))
        }.single()

        assertEquals("xpubETH", result.key)
        assertArrayEquals(byteArrayOf(0x0a, 0x0b), result.publicKey)
        assertArrayEquals(byteArrayOf(0x0c, 0x0d), result.chainCode)
    }

    @Test
    fun getPublicKeys_bitcoinFamily_bundlesWithShortCoinNamesInRequestOrder() = runBlocking {
        val paramsSlot = slot<JsonObject>()
        coEvery { manager.call(TrezorMethod.BtcGetPublicKey, capture(paramsSlot)) } returns TrezorResponse(
            success = true,
            payload = buildJsonArray {
                add(buildJsonObject { put("xpub", "xpubBTC") })
                add(buildJsonObject { put("xpub", "xpubLTC") })
            }
        )

        val results = client.connect {
            getPublicKeys(
                listOf(
                    TrezorPublicKeyRequest.Bitcoin("Bitcoin", TrezorDerivationPath.parse("m/84'/0'/0'"), TrezorInputScriptType.SPENDWITNESS),
                    TrezorPublicKeyRequest.Bitcoin("Litecoin", TrezorDerivationPath.parse("m/84'/2'/0'"), TrezorInputScriptType.SPENDWITNESS)
                )
            )
        }

        assertEquals(listOf("xpubBTC", "xpubLTC"), results.map { it.key })

        val bundle = requireNotNull(paramsSlot.captured["bundle"]).jsonArray
        assertEquals("btc", bundle[0].jsonObject["coin"]?.jsonPrimitive?.content)
        assertEquals("m/84'/0'/0'", bundle[0].jsonObject["path"]?.jsonPrimitive?.content)
        assertEquals("ltc", bundle[1].jsonObject["coin"]?.jsonPrimitive?.content)
    }
}

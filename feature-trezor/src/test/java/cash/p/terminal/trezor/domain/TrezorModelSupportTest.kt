package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorModelSupportTest {

    private fun features(supportsTron: Boolean) = TrezorFeatures(
        deviceId = "device-1",
        model = "T",
        internalModel = "T3B1",
        firmwareVersion = "2.11.0",
        passphraseProtection = false,
        supportsTron = supportsTron,
    )

    @Test
    fun allModels_supportBitcoin() {
        TrezorModel.entries.forEach { model ->
            assertTrue(TrezorModelSupport.isSupported(model, BlockchainType.Bitcoin))
        }
    }

    @Test
    fun allModels_supportEthereum() {
        TrezorModel.entries.forEach { model ->
            assertTrue(TrezorModelSupport.isSupported(model, BlockchainType.Ethereum))
        }
    }

    @Test
    fun onlyT1B1AndT2T1_supportDash() {
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.One, BlockchainType.Dash))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.ModelT, BlockchainType.Dash))
        assertFalse(TrezorModelSupport.isSupported(TrezorModel.Safe3, BlockchainType.Dash))
        assertFalse(TrezorModelSupport.isSupported(TrezorModel.Safe5, BlockchainType.Dash))
        assertFalse(TrezorModelSupport.isSupported(TrezorModel.Safe7, BlockchainType.Dash))
    }

    @Test
    fun allModels_supportStellar() {
        TrezorModel.entries.forEach { model ->
            assertTrue(TrezorModelSupport.isSupported(model, BlockchainType.Stellar))
        }
    }

    @Test
    fun tron_supportedOnModelTAndSafe() {
        assertFalse(TrezorModelSupport.isSupported(TrezorModel.One, BlockchainType.Tron))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.ModelT, BlockchainType.Tron))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe3, BlockchainType.Tron))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe5, BlockchainType.Tron))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe7, BlockchainType.Tron))
    }

    @Test
    fun solana_supportedOnModelTAndSafe() {
        assertFalse(TrezorModelSupport.isSupported(TrezorModel.One, BlockchainType.Solana))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.ModelT, BlockchainType.Solana))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe3, BlockchainType.Solana))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe5, BlockchainType.Solana))
        assertTrue(TrezorModelSupport.isSupported(TrezorModel.Safe7, BlockchainType.Solana))
    }

    @Test
    fun allModels_excludeMonero() {
        TrezorModel.entries.forEach { model ->
            assertFalse(TrezorModelSupport.isSupported(model, BlockchainType.Monero))
        }
    }

    @Test
    fun nullModel_returnsUniversalOnly() {
        val supported = TrezorModelSupport.getSupportedBlockchains(null)
        assertTrue(supported.contains(BlockchainType.Bitcoin))
        assertTrue(supported.contains(BlockchainType.Ethereum))
        assertTrue(supported.contains(BlockchainType.Stellar))
        assertFalse(supported.contains(BlockchainType.Tron))
        assertFalse(supported.contains(BlockchainType.Solana))
        assertFalse(supported.contains(BlockchainType.Dash))
    }

    @Test
    fun fromInternalModel_bothSafe3Generations_mapToSafe3() {
        assertEquals(TrezorModel.Safe3, TrezorModel.fromInternalModel("T2B1"))
        assertEquals(TrezorModel.Safe3, TrezorModel.fromInternalModel("T3B1"))
    }

    @Test
    fun fromInternalModel_knownAndUnknownCodes_mapAsExpected() {
        assertEquals(TrezorModel.One, TrezorModel.fromInternalModel("T1B1"))
        assertEquals(TrezorModel.ModelT, TrezorModel.fromInternalModel("T2T1"))
        assertEquals(TrezorModel.Safe5, TrezorModel.fromInternalModel("T3T1"))
        assertEquals(TrezorModel.Safe7, TrezorModel.fromInternalModel("T3W1"))
        assertNull(TrezorModel.fromInternalModel("UNKNOWN"))
        assertNull(TrezorModel.fromInternalModel(null))
    }

    @Test
    fun filterByFirmwareCapabilities_noTronCapability_dropsTronQueries() {
        val bitcoin = TokenQuery(BlockchainType.Bitcoin, TokenType.Native)
        val queries = listOf(
            bitcoin,
            TokenQuery(BlockchainType.Tron, TokenType.Native),
            TokenQuery(BlockchainType.Tron, TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")),
        )

        val result = TrezorModelSupport.filterByFirmwareCapabilities(queries, features(supportsTron = false))

        assertEquals(listOf(bitcoin), result)
    }

    @Test
    fun filterByFirmwareCapabilities_tronCapability_keepsTronQueries() {
        val queries = listOf(
            TokenQuery(BlockchainType.Bitcoin, TokenType.Native),
            TokenQuery(BlockchainType.Tron, TokenType.Native),
        )

        val result = TrezorModelSupport.filterByFirmwareCapabilities(queries, features(supportsTron = true))

        assertEquals(queries, result)
    }
}

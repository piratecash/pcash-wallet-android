package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorModelSupportTest {

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
    fun allModels_excludeTron() {
        // Deep link API doesn't support tronGetAddress yet
        TrezorModel.entries.forEach { model ->
            assertFalse(TrezorModelSupport.isSupported(model, BlockchainType.Tron))
        }
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
}

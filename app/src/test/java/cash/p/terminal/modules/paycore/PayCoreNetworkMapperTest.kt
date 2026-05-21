package cash.p.terminal.modules.paycore

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayCoreNetworkMapperTest {

    private fun makeToken(coinUid: String, coinCode: String, blockchainType: BlockchainType): Token {
        return Token(
            coin = Coin(uid = coinUid, name = coinCode, code = coinCode, marketCapRank = null, coinGeckoId = null, image = null),
            blockchain = Blockchain(blockchainType, blockchainType.uid, null),
            type = TokenType.Native,
            decimals = 6
        )
    }

    // --- isUsdtOnSupportedNetwork ---

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnEthereum_returnsTrue() {
        val token = makeToken("tether", "USDT", BlockchainType.Ethereum)
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnBsc_returnsTrue() {
        val token = makeToken("tether", "BSC-USD", BlockchainType.BinanceSmartChain)
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnTron_returnsTrue() {
        val token = makeToken("tether", "USDT", BlockchainType.Tron)
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnSolana_returnsFalse() {
        val token = makeToken("tether", "USDT", BlockchainType.Solana)
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_nonTetherCoin_returnsFalse() {
        val token = makeToken("ethereum", "ETH", BlockchainType.Ethereum)
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_rubToken_returnsFalse() {
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(PayCoreAssets.rubToken))
    }

    // --- toNetworkType ---

    @Test
    fun toNetworkType_rub_returnsRUB() {
        assertEquals("RUB", PayCoreNetworkMapper.toNetworkType(PayCoreAssets.rubToken))
    }

    @Test
    fun toNetworkType_ethereum_returnsERC20() {
        val token = makeToken("tether", "USDT", BlockchainType.Ethereum)
        assertEquals("ERC20", PayCoreNetworkMapper.toNetworkType(token))
    }

    @Test
    fun toNetworkType_bsc_returnsBEP20() {
        val token = makeToken("tether", "BSC-USD", BlockchainType.BinanceSmartChain)
        assertEquals("BEP20", PayCoreNetworkMapper.toNetworkType(token))
    }

    @Test
    fun toNetworkType_tron_returnsTRC20() {
        val token = makeToken("tether", "USDT", BlockchainType.Tron)
        assertEquals("TRC20", PayCoreNetworkMapper.toNetworkType(token))
    }

    @Test
    fun toNetworkType_unsupportedChain_returnsNull() {
        val token = makeToken("tether", "USDT", BlockchainType.Solana)
        assertNull(PayCoreNetworkMapper.toNetworkType(token))
    }

    @Test
    fun fromBlockchainTypeUid_tron_returnsTRC20() {
        assertEquals("TRC20", PayCoreNetworkMapper.fromBlockchainTypeUid("tron"))
    }

    @Test
    fun fromCurrencies_rubToErc20_returnsERC20() {
        assertEquals("ERC20", PayCoreNetworkMapper.fromCurrencies("RUB", "ERC20"))
    }

    @Test
    fun fromCurrencies_trc20ToRub_returnsTRC20() {
        assertEquals("TRC20", PayCoreNetworkMapper.fromCurrencies("TRC20", "RUB"))
    }

    @Test
    fun fromCurrencies_rubToRub_returnsNull() {
        assertNull(PayCoreNetworkMapper.fromCurrencies("RUB", "RUB"))
    }
}

package cash.p.terminal.modules.paycore

import cash.p.terminal.modules.paycore.PayCoreNetworkMapper.toTicker
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

class PayCoreTickerMapperTest {

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
    fun isUsdtOnSupportedNetwork_tetherOnBsc_returnsFalse() {
        val token = makeToken("tether", "BSC-USD", BlockchainType.BinanceSmartChain)
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnTron_returnsTrue() {
        val token = makeToken("tether", "USDT", BlockchainType.Tron)
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
    }

    @Test
    fun isUsdtOnSupportedNetwork_tetherOnSolana_returnsTrue() {
        val token = makeToken("tether", "USDT", BlockchainType.Solana)
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(token))
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

    // --- toTicker ---

    @Test
    fun toTicker_rub_returnsRub() {
        assertEquals(PayCoreTicker.RUB, PayCoreAssets.rubToken.toTicker())
    }

    @Test
    fun toTicker_ethereum_returnsUsdtErc20() {
        val token = makeToken("tether", "USDT", BlockchainType.Ethereum)
        assertEquals(PayCoreTicker.USDT_ERC20, token.toTicker())
    }

    @Test
    fun toTicker_bsc_returnsNull() {
        val token = makeToken("tether", "BSC-USD", BlockchainType.BinanceSmartChain)
        assertNull(token.toTicker())
    }

    @Test
    fun toTicker_tron_returnsUsdt() {
        val token = makeToken("tether", "USDT", BlockchainType.Tron)
        assertEquals(PayCoreTicker.USDT, token.toTicker())
    }

    @Test
    fun toTicker_solana_returnsUsdtSpl() {
        val token = makeToken("tether", "USDT", BlockchainType.Solana)
        assertEquals(PayCoreTicker.USDT_SPL, token.toTicker())
    }

    @Test
    fun payCoreNetworkTypeFromBlockchainTypeUid_tron_returnsUsdt() {
        assertEquals(
            PayCoreTicker.USDT,
            PayCoreNetworkMapper.payCoreNetworkTypeFromBlockchainTypeUid("tron"),
        )
    }
}

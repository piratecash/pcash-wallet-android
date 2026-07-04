package cash.p.terminal.modules.paycore

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for PayCore coin selection logic:
 * - RUB section appears when other token is USDT on supported network
 * - USDT-only filter activates when other token is RUB
 * - Solana USDT is recognized as supported
 *
 * These test the pure functions that SwapSelectCoinViewModel relies on.
 * The ViewModel itself uses App.* singletons and can't be unit-tested in isolation.
 */
class PayCoreCoinSelectionTest {

    private val usdtErc20 = makeToken("tether", "USDT", BlockchainType.Ethereum)
    private val usdtTrc20 = makeToken("tether", "USDT", BlockchainType.Tron)
    private val usdtSol = makeToken("tether", "USDT", BlockchainType.Solana)
    private val bscUsd = makeToken("tether", "BSC-USD", BlockchainType.BinanceSmartChain)
    private val eth = makeToken("ethereum", "ETH", BlockchainType.Ethereum)
    private val btc = makeToken("bitcoin", "BTC", BlockchainType.Bitcoin)
    private val rub = PayCoreAssets.rubToken

    // --- "Show fiat section" condition: other is USDT on supported network ---

    @Test
    fun showFiatSection_otherIsUsdtErc20_true() {
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(usdtErc20))
    }

    @Test
    fun showFiatSection_otherIsUsdtTrc20_true() {
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(usdtTrc20))
    }

    @Test
    fun showFiatSection_otherIsUsdtSol_true() {
        assertTrue(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(usdtSol))
    }

    @Test
    fun showFiatSection_otherIsBscUsd_false() {
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(bscUsd))
    }

    @Test
    fun showFiatSection_otherIsEth_false() {
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(eth))
    }

    @Test
    fun showFiatSection_otherIsBtc_false() {
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(btc))
    }

    @Test
    fun showFiatSection_otherIsRub_false() {
        assertFalse(PayCoreNetworkMapper.isUsdtOnSupportedNetwork(rub))
    }

    // --- "Filter USDT only" condition: other is RUB ---

    @Test
    fun filterUsdtOnly_otherIsRub_true() {
        assertTrue(PayCoreAssets.isRub(rub))
    }

    @Test
    fun filterUsdtOnly_otherIsUsdt_false() {
        assertFalse(PayCoreAssets.isRub(usdtErc20))
    }

    // --- USDT filter includes supported networks only ---

    @Test
    fun usdtFilter_includesErc20() {
        val tokens = listOf(usdtErc20, eth, btc)
        val filtered = tokens.filter { PayCoreNetworkMapper.isUsdtOnSupportedNetwork(it) }
        assertTrue(filtered.contains(usdtErc20))
        assertFalse(filtered.contains(eth))
    }

    @Test
    fun usdtFilter_excludesBscUsd() {
        val tokens = listOf(bscUsd, eth, btc)
        val filtered = tokens.filter { PayCoreNetworkMapper.isUsdtOnSupportedNetwork(it) }
        assertFalse(filtered.contains(bscUsd))
    }

    @Test
    fun usdtFilter_includesSol() {
        val tokens = listOf(usdtSol, eth, btc)
        val filtered = tokens.filter { PayCoreNetworkMapper.isUsdtOnSupportedNetwork(it) }
        assertTrue(filtered.contains(usdtSol))
    }

    @Test
    fun usdtFilter_includesTrc20() {
        val tokens = listOf(usdtTrc20, eth, btc)
        val filtered = tokens.filter { PayCoreNetworkMapper.isUsdtOnSupportedNetwork(it) }
        assertTrue(filtered.contains(usdtTrc20))
    }

    @Test
    fun usdtFilter_excludesNonTether() {
        val tokens = listOf(eth, btc)
        val filtered = tokens.filter { PayCoreNetworkMapper.isUsdtOnSupportedNetwork(it) }
        assertTrue(filtered.isEmpty())
    }

    private fun makeToken(coinUid: String, coinCode: String, blockchainType: BlockchainType): Token {
        return Token(
            coin = Coin(uid = coinUid, name = coinCode, code = coinCode, marketCapRank = null, coinGeckoId = null, image = null),
            blockchain = Blockchain(blockchainType, blockchainType.uid, null),
            type = TokenType.Native,
            decimals = 6
        )
    }
}

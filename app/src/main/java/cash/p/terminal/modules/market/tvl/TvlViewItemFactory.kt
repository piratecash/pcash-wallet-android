package cash.p.terminal.modules.market.tvl

import cash.p.terminal.R
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.ui.compose.Select
import cash.p.terminal.strings.helpers.TranslatableString

class TvlViewItemFactory {
    private val cache: MutableMap<Int, TvlModule.CoinTvlViewItem> = hashMapOf()

    fun tvlData(
        chain: TvlModule.Chain,
        chains: List<TvlModule.Chain>,
        sortDescending: Boolean,
        tvlItems: List<TvlModule.MarketTvlItem>
    ) = TvlModule.TvlData(
        Select(chain, chains),
        sortDescending,
        tvlItems.mapNotNull {
            coinTvlViewItem(it)
        })

    private fun coinTvlViewItem(item: TvlModule.MarketTvlItem): TvlModule.CoinTvlViewItem? {
        if (!cache.containsKey(item.hashCode())) {
            val viewItem = TvlModule.CoinTvlViewItem(
                item.fullCoin?.coin?.uid,
                tvl = item.tvl,
                tvlChangePercent = item.diffPercent,
                tvlChangeAmount = item.diff,
                rank = item.rank,
                name = item.fullCoin?.coin?.name ?: item.name,
                chain = if (item.chains.size > 1) {
                    TranslatableString.ResString(R.string.TvlRank_MultiChain)
                } else if(item.chains.size == 1) {
                    TranslatableString.PlainString(item.chains.first())
                } else {
                    TranslatableString.PlainString("")
                },
                iconUrl = item.fullCoin?.coin?.imageUrl ?: item.iconUrl,
                iconPlaceholder = item.fullCoin?.iconPlaceholder
            )

            cache[item.hashCode()] = viewItem
        }
        return cache[item.hashCode()]
    }
}

package cash.p.terminal.modules.market.topnftcollections

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.sortedByDescendingNullLast
import cash.p.terminal.modules.market.sortedByNullLast
import cash.p.terminal.modules.nft.NftCollectionItem
import cash.p.terminal.modules.nft.nftCollectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TopNftCollectionsRepository(
    private val marketKit: MarketKitWrapper
) {
    private var itemsCache: List<NftCollectionItem>? = null


    suspend fun get(
        sortingField: SortingField,
        timeDuration: TimeDuration,
        forceRefresh: Boolean,
        limit: Int? = null,
    ) = withContext(Dispatchers.IO) {
        val currentCache = itemsCache

        val items = if (forceRefresh || currentCache == null) {
            marketKit.nftCollections().map { it.nftCollectionItem }
        } else {
            currentCache
        }

        itemsCache = items

         items.sort(sortingField, timeDuration).let { sortedList ->
            limit?.let { sortedList.take(it) } ?: sortedList
        }
    }

    private fun List<NftCollectionItem>.sort(sortingField: SortingField, timeDuration: TimeDuration) =
        when (sortingField) {
            SortingField.HighestCap,
            SortingField.LowestCap -> this
            SortingField.HighestVolume -> sortedByDescendingNullLast { it.volume(timeDuration)?.value }
            SortingField.LowestVolume -> sortedByNullLast { it.volume(timeDuration)?.value }
            SortingField.TopGainers -> sortedByDescendingNullLast { it.volumeDiff(timeDuration) }
            SortingField.TopLosers -> sortedByNullLast { it.volumeDiff(timeDuration) }
        }

    private fun NftCollectionItem.volume(timeDuration: TimeDuration) =
        when (timeDuration) {
            TimeDuration.OneDay -> oneDayVolume
            TimeDuration.SevenDay -> sevenDayVolume
            TimeDuration.ThirtyDay -> thirtyDayVolume
        }

    private fun NftCollectionItem.volumeDiff(timeDuration: TimeDuration) =
        when (timeDuration) {
            TimeDuration.OneDay -> oneDayVolumeDiff
            TimeDuration.SevenDay -> sevenDayVolumeDiff
            TimeDuration.ThirtyDay -> thirtyDayVolumeDiff
        }

}

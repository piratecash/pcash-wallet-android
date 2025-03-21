package cash.p.terminal.modules.market.overview.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

@Composable
fun MarketsHorizontalCards(
    pageCount: Int,
    pageContent: @Composable() (PagerScope.(page: Int) -> Unit)
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        pageSize = object : PageSize {
            override fun Density.calculateMainAxisPageSize(
                availableSpace: Int,
                pageSpacing: Int,
            ): Int {
                return (availableSpace - 1 * pageSpacing) / 2
            }
        },
        contentPadding = PaddingValues(16.dp),
        pageSpacing = 8.dp,
        pageContent = pageContent,
    )
}
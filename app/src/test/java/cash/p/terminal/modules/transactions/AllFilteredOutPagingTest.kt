package cash.p.terminal.modules.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [requestNextPageIfAllFilteredOut] - the shared paging trigger used by both
 * TransactionsViewModel and TokenBalanceViewModel when a whole page is hidden by [isVisibleFor]
 * (e.g. only swaps on the Incoming/Outgoing filter).
 */
class AllFilteredOutPagingTest {

    @Test
    fun requestNextPageIfAllFilteredOut_pageAllFiltered_requestsNextPage() {
        var loadNextCalls = 0

        requestNextPageIfAllFilteredOut(rawItemCount = 20, visibleItemCount = 0) { loadNextCalls++ }

        assertEquals(1, loadNextCalls)
    }

    @Test
    fun requestNextPageIfAllFilteredOut_pageHasVisibleRow_doesNotRequestNextPage() {
        var loadNextCalls = 0

        requestNextPageIfAllFilteredOut(rawItemCount = 20, visibleItemCount = 3) { loadNextCalls++ }

        assertEquals(0, loadNextCalls)
    }

    @Test
    fun requestNextPageIfAllFilteredOut_emptyPage_doesNotRequestNextPage() {
        var loadNextCalls = 0

        requestNextPageIfAllFilteredOut(rawItemCount = 0, visibleItemCount = 0) { loadNextCalls++ }

        assertEquals(0, loadNextCalls)
    }
}

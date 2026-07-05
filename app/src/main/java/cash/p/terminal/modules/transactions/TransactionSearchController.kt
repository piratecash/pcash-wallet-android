package cash.p.terminal.modules.transactions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reusable search-mode state machine: active/inactive toggle and debounced query
 * changes. Shared by screens with query-based transaction search (Transactions
 * screen, Asset/Token screen).
 *
 * The host owns applying the query to its own data source (e.g. via a repository
 * or service call) and mapping [searchActive]/[searchQuery] into its own UI state.
 * Anything tied to the host's data source — such as search-scan progress — stays
 * in the host, since it is not part of this generic state machine.
 */
class TransactionSearchController(
    private val coroutineScope: CoroutineScope,
    private val host: Host,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) {
    var searchActive: Boolean = false
        private set
    var searchQuery: String = ""
        private set

    private var searchDebounceJob: Job? = null

    fun onSearchClick() {
        searchActive = true
        host.onSearchStateChanged()
    }

    fun onSearchQueryChange(query: String) {
        if (searchQuery == query) return

        searchQuery = query
        host.onSearchStateChanged()

        searchDebounceJob?.cancel()
        searchDebounceJob = coroutineScope.launch {
            delay(debounceMillis)
            host.applySearchQuery(searchQuery.trim())
        }
    }

    fun onSearchClose() {
        searchDebounceJob?.cancel()
        searchActive = false
        searchQuery = ""
        host.onSearchStateChanged()
        searchDebounceJob = coroutineScope.launch {
            host.applySearchQuery("")
        }
    }

    interface Host {
        fun onSearchStateChanged()
        suspend fun applySearchQuery(query: String)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

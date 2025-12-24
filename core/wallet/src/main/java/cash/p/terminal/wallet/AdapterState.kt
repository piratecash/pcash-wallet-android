package cash.p.terminal.wallet

import java.util.Date

sealed class AdapterState {
    object Synced : AdapterState()
    object Connecting : AdapterState()
    data class Syncing(
        val progress: Int? = null,
        val lastBlockDate: Date? = null,
        val blocksRemained: Long? = null
    ) : AdapterState()
    data class SearchingTxs(val count: Int) : AdapterState()
    data class NotSynced(val error: Throwable) : AdapterState()

    override fun toString(): String {
        return when (this) {
            is Synced -> "Synced"
            is Connecting -> "Connecting"
            is Syncing -> "Syncing ${progress?.let { "$it%" } ?: ""} blocksRemained: $blocksRemained lastBlockDate: $lastBlockDate"
            is SearchingTxs -> "SearchingTxs count: $count"
            is NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
        }
    }
}
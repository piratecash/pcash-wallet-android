package cash.p.terminal.wallet

import java.util.Date

sealed class AdapterState {
    object Synced : AdapterState()
    object Connecting : AdapterState()
    data class Syncing(
        val progress: Int? = null,
        val lastBlockDate: Date? = null,
        val blocksRemained: Long? = null,
        val substatus: Substatus? = null
    ) : AdapterState()
    data class SearchingTxs(val count: Int) : AdapterState()
    data class NotSynced(val error: Throwable) : AdapterState()

    sealed class Substatus {
        data class WaitingForPeers(val connected: Int, val required: Int) : Substatus()
    }

    override fun toString(): String {
        return when (this) {
            is Synced -> "Synced"
            is Connecting -> "Connecting"
            is Syncing -> {
                val sub = substatus?.let { " substatus: $it" }.orEmpty()
                "Syncing ${progress?.let { "$it%" }.orEmpty()} blocksRemained: $blocksRemained lastBlockDate: $lastBlockDate$sub"
            }
            is SearchingTxs -> "SearchingTxs count: $count"
            is NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
        }
    }
}
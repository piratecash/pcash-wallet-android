package cash.p.terminal.modules.send.zcash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class SendZCashMemoService {
    val memoMaxLength = 120

    private var memo: String = ""
    private var memoIsAllowed = false

    private val _stateFlow = MutableStateFlow(
        State(memo = memo, memoIsAllowed = false)
    )
    val stateFlow = _stateFlow.asStateFlow()

    fun setMemo(memo: String) {
        this.memo = memo
        emitState()
    }

    fun setAddress(address: String) {
        memoIsAllowed = isMemoAllowedForAddress(address)
        emitState()
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                memo = if (memoIsAllowed) memo else "",
                memoIsAllowed = memoIsAllowed
            )
        }
    }

    private fun isMemoAllowedForAddress(address: String): Boolean {
        val lower = address.trim().lowercase(Locale.ROOT)

        // Sapling shielded addresses - always support memo
        if (lower.startsWith("zs") || lower.startsWith("ztestsapling")) return true

        // Transparent addresses - never support memo
        if (lower.startsWith("t1") || lower.startsWith("t3")) return false

        // Sprout addresses (deprecated but technically support memo)
        if (lower.startsWith("zc")) return true

        // Unified address parsing according to ZIP-316
        if (lower.startsWith("u")) return true

        // Unknown format - conservatively disallow memo
        return false
    }

    data class State(
        val memo: String,
        val memoIsAllowed: Boolean
    )
}

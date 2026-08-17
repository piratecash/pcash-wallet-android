package cash.p.terminal.wallet

interface IAdapter {
    /** Attach local collectors and local reads only — no network side effects. */
    fun attachLocalData()

    /** Stop the network side, leave local data untouched. */
    fun pauseNetwork()

    fun resumeNetwork()

    fun start() {
        attachLocalData()
        resumeNetwork()
    }

    fun stop()
    suspend fun refresh()

    val debugInfo: String
    val statusInfo: Map<String, Any>
}
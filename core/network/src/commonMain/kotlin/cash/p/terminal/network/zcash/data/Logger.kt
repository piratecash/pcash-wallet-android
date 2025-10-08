package cash.p.terminal.network.zcash.data

internal expect class Logger() {
    fun log(date: String, error: Throwable)
}

package cash.p.terminal.network.data

internal expect object NetworkLogger {
    fun debug(message: String, error: Throwable? = null)
    fun warning(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

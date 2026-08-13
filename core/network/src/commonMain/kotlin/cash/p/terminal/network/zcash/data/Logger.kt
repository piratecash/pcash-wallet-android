package cash.p.terminal.network.zcash.data

import cash.p.terminal.network.data.NetworkLogger

internal class Logger {
    fun log(date: String, error: Throwable) {
        NetworkLogger.warning("Failed to load Zcash height for $date", error)
    }
}

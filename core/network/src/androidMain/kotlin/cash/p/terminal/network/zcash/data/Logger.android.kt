package cash.p.terminal.network.zcash.data

import timber.log.Timber

internal actual class Logger actual constructor() {
    actual fun log(date: String, error: Throwable) {
        Timber.w(error, "Failed to load Zcash height for %s", date)
    }
}

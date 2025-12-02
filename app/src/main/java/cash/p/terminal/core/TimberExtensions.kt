package cash.p.terminal.core

import timber.log.Timber

fun logError(t: Throwable, message: String) {
    Timber.e(t, message)
}

package cash.p.terminal.network.data

import timber.log.Timber

internal actual object NetworkLogger {
    actual fun debug(message: String, error: Throwable?) {
        Timber.d(error, message)
    }

    actual fun warning(message: String, error: Throwable?) {
        Timber.w(error, message)
    }

    actual fun error(message: String, error: Throwable?) {
        Timber.e(error, message)
    }
}

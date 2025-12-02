package cash.p.terminal.core

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

fun logError(t: Throwable, message: String) {
    try {
        FirebaseCrashlytics.getInstance().apply {
            log(message)
            recordException(t)
        }
    } catch (_: Throwable) {

    }
    Timber.e(t, message)
}

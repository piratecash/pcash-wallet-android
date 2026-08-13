package cash.p.terminal.network.data

import java.util.logging.Level
import java.util.logging.Logger

internal actual object NetworkLogger {
    private val logger = Logger.getLogger("cash.p.terminal.network")

    actual fun debug(message: String, error: Throwable?) {
        logger.log(Level.FINE, message, error)
    }

    actual fun warning(message: String, error: Throwable?) {
        logger.log(Level.WARNING, message, error)
    }

    actual fun error(message: String, error: Throwable?) {
        logger.log(Level.SEVERE, message, error)
    }
}

package cash.p.terminal.network.zcash.data

internal actual class Logger actual constructor() {
    actual fun log(date: String, error: Throwable) {
        println("Failed to load Zcash height for $date: ${error.message}")
    }
}

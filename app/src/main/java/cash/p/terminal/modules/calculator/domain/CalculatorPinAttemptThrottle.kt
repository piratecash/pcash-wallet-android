package cash.p.terminal.modules.calculator.domain

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.pin.core.UptimeProvider

class CalculatorPinAttemptThrottle(
    private val storage: ILocalStorage,
    private val uptimeProvider: UptimeProvider,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val refillIntervalMillisProvider: () -> Long = {
        storage.calculatorAutoLockOption.refillIntervalMillis
    },
) {

    fun tryConsume(): Boolean {
        val nowUptime = uptimeProvider.uptime
        val nowWall = wallClock()
        rebaselineIfRebooted(nowUptime, nowWall)

        val available = currentTokens(nowUptime, nowWall)

        if (available <= 0) {
            return false
        }

        persistState(available - 1, nowUptime, nowWall)
        return true
    }

    fun reset() {
        persistState(capacity, uptimeProvider.uptime, wallClock())
    }

    private fun rebaselineIfRebooted(nowUptime: Long, nowWall: Long) {
        if (storage.calculatorThrottleLastUptime > nowUptime) {
            storage.calculatorThrottleLastUptime = nowUptime
            storage.calculatorThrottleLastWallClock = nowWall
        }
    }

    private fun persistState(tokens: Int, uptime: Long, wallClock: Long) {
        storage.calculatorThrottleTokens = tokens
        storage.calculatorThrottleLastUptime = uptime
        storage.calculatorThrottleLastWallClock = wallClock
    }

    private fun currentTokens(nowUptime: Long, nowWall: Long): Int {
        val storedTokens = storage.calculatorThrottleTokens
        if (storedTokens == Int.MIN_VALUE) {
            return capacity
        }

        val elapsed = elapsedSince(
            nowUptime = nowUptime,
            storedUptime = storage.calculatorThrottleLastUptime,
            nowWall = nowWall,
            storedWall = storage.calculatorThrottleLastWallClock,
        )
        val refillIntervalMillis = refillIntervalMillisProvider()
        val refilled = if (refillIntervalMillis > 0) {
            (elapsed / refillIntervalMillis).toInt()
        } else {
            capacity
        }
        return (storedTokens + refilled).coerceIn(0, capacity)
    }

    private fun elapsedSince(
        nowUptime: Long,
        storedUptime: Long,
        nowWall: Long,
        storedWall: Long,
    ): Long {
        val uptimeDelta = nowUptime - storedUptime
        val wallDelta = nowWall - storedWall
        if (uptimeDelta < 0 || wallDelta < 0) return 0L
        return minOf(uptimeDelta, wallDelta)
    }

    companion object {
        private const val DEFAULT_CAPACITY = 5
    }
}

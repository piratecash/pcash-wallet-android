package cash.p.terminal.modules.calculator.domain

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.pin.core.UptimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculatorPinAttemptThrottleTest {

    private lateinit var storage: ILocalStorage
    private lateinit var uptimeProvider: UptimeProvider
    private var wallClockTime = 0L
    private lateinit var throttle: CalculatorPinAttemptThrottle

    private val capacity = 5
    private var refillIntervalMillis = 240_000L

    @Before
    fun setUp() {
        storage = mockk(relaxed = true)
        uptimeProvider = mockk()
        wallClockTime = 1_000_000L
        refillIntervalMillis = 240_000L
        throttle = CalculatorPinAttemptThrottle(
            storage = storage,
            uptimeProvider = uptimeProvider,
            wallClock = { wallClockTime },
            capacity = capacity,
            refillIntervalMillisProvider = { refillIntervalMillis },
        )
    }

    @Test
    fun tryConsume_firstTimeNoStoredState_grantsTokenAndStoresCapacityMinusOne() {
        every { storage.calculatorThrottleTokens } returns Int.MIN_VALUE
        every { storage.calculatorThrottleLastUptime } returns 0L
        every { storage.calculatorThrottleLastWallClock } returns 0L
        every { uptimeProvider.uptime } returns 1_000L

        val result = throttle.tryConsume()

        assertTrue(result)
        verify { storage.calculatorThrottleTokens = capacity - 1 }
        verify { storage.calculatorThrottleLastUptime = 1_000L }
        verify { storage.calculatorThrottleLastWallClock = wallClockTime }
    }

    @Test
    fun tryConsume_tokensAvailable_decrementsAndReturnsTrue() {
        every { storage.calculatorThrottleTokens } returns 3
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - 500L
        every { uptimeProvider.uptime } returns 1_500L

        val result = throttle.tryConsume()

        assertTrue(result)
        verify { storage.calculatorThrottleTokens = 2 }
        verify { storage.calculatorThrottleLastUptime = 1_500L }
        verify { storage.calculatorThrottleLastWallClock = wallClockTime }
    }

    @Test
    fun tryConsume_zeroTokensNoElapsedRefill_returnsFalseAndPreservesBaseline() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - 500L
        every { uptimeProvider.uptime } returns 1_500L

        val result = throttle.tryConsume()

        assertFalse(result)
        verify(exactly = 0) { storage.calculatorThrottleTokens = any() }
        verify(exactly = 0) { storage.calculatorThrottleLastUptime = any() }
        verify(exactly = 0) { storage.calculatorThrottleLastWallClock = any() }
    }

    @Test
    fun tryConsume_zeroTokensRepeatedlyDenied_baselineNotPushedForward() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime
        var currentUptime = 1_500L
        every { uptimeProvider.uptime } answers { currentUptime }

        repeat(10) {
            throttle.tryConsume()
            currentUptime += 1_000L
            wallClockTime += 1_000L
        }

        verify(exactly = 0) { storage.calculatorThrottleTokens = any() }
        verify(exactly = 0) { storage.calculatorThrottleLastUptime = any() }
        verify(exactly = 0) { storage.calculatorThrottleLastWallClock = any() }
    }

    @Test
    fun tryConsume_zeroTokensFullRefillIntervalElapsedOnBothClocks_grantsToken() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - refillIntervalMillis
        every { uptimeProvider.uptime } returns 1_000L + refillIntervalMillis

        val result = throttle.tryConsume()

        assertTrue(result)
        verify { storage.calculatorThrottleTokens = 0 }
    }

    @Test
    fun tryConsume_partialIntervalElapsed_doesNotRefill() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - (refillIntervalMillis - 1)
        every { uptimeProvider.uptime } returns 1_000L + refillIntervalMillis - 1

        val result = throttle.tryConsume()

        assertFalse(result)
    }

    @Test
    fun tryConsume_multipleIntervalsElapsedCappedAtCapacity_grantsToken() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - refillIntervalMillis * 100L
        every { uptimeProvider.uptime } returns 1_000L + refillIntervalMillis * 100L

        val result = throttle.tryConsume()

        assertTrue(result)
        verify { storage.calculatorThrottleTokens = capacity - 1 }
    }

    @Test
    fun tryConsume_uptimeRegressedAfterReboot_rebaselinesAndDoesNotGrantFreeRefill() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 10_000_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - refillIntervalMillis
        every { uptimeProvider.uptime } returns 500L

        val result = throttle.tryConsume()

        assertFalse(result)
        verify { storage.calculatorThrottleLastUptime = 500L }
        verify { storage.calculatorThrottleLastWallClock = wallClockTime }
        verify(exactly = 0) { storage.calculatorThrottleTokens = any() }
    }

    @Test
    fun tryConsume_rebootPlusFutureWallClock_doesNotBypassThrottle() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 10_000_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - refillIntervalMillis * 100L
        every { uptimeProvider.uptime } returns 500L

        val result = throttle.tryConsume()

        assertFalse(result)
        verify(exactly = 0) { storage.calculatorThrottleTokens = any() }
    }

    @Test
    fun tryConsume_uptimeForwardWallClockBackward_doesNotGrantRefill() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime + refillIntervalMillis * 10L
        every { uptimeProvider.uptime } returns 1_000L + refillIntervalMillis * 10L

        val result = throttle.tryConsume()

        assertFalse(result)
    }

    @Test
    fun tryConsume_fiveSequentialAttemptsThenSixthDenied() {
        var tokens = Int.MIN_VALUE
        var lastUptime = 0L
        var lastWall = 0L
        every { storage.calculatorThrottleTokens } answers { tokens }
        every { storage.calculatorThrottleLastUptime } answers { lastUptime }
        every { storage.calculatorThrottleLastWallClock } answers { lastWall }
        every { storage.calculatorThrottleTokens = any() } answers { tokens = firstArg() }
        every { storage.calculatorThrottleLastUptime = any() } answers { lastUptime = firstArg() }
        every { storage.calculatorThrottleLastWallClock = any() } answers { lastWall = firstArg() }
        every { uptimeProvider.uptime } returns 1_000L

        val results = (0 until 6).map { throttle.tryConsume() }

        assertEquals(listOf(true, true, true, true, true, false), results)
    }

    @Test
    fun tryConsume_refillIntervalChangesBetweenCalls_usesLatestValue() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime - 30_000L
        every { uptimeProvider.uptime } returns 1_000L + 30_000L

        refillIntervalMillis = 240_000L
        assertFalse(throttle.tryConsume())

        refillIntervalMillis = 30_000L
        assertTrue(throttle.tryConsume())
    }

    @Test
    fun tryConsume_refillIntervalZero_grantsFullCapacityImmediately() {
        every { storage.calculatorThrottleTokens } returns 0
        every { storage.calculatorThrottleLastUptime } returns 1_000L
        every { storage.calculatorThrottleLastWallClock } returns wallClockTime
        every { uptimeProvider.uptime } returns 1_000L
        refillIntervalMillis = 0L

        val result = throttle.tryConsume()

        assertTrue(result)
        verify { storage.calculatorThrottleTokens = capacity - 1 }
    }

    @Test
    fun reset_setsTokensToCapacityAndStampsBothClocks() {
        every { uptimeProvider.uptime } returns 7_000L

        throttle.reset()

        verify { storage.calculatorThrottleTokens = capacity }
        verify { storage.calculatorThrottleLastUptime = 7_000L }
        verify { storage.calculatorThrottleLastWallClock = wallClockTime }
    }
}

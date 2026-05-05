package cash.p.terminal.modules.balance

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatProgressPercentTest {

    @Test
    fun zero_returnsZero() {
        assertEquals("0", formatProgressPercent(0.0))
    }

    @Test
    fun negative_returnsZero() {
        assertEquals("0", formatProgressPercent(-0.5))
    }

    @Test
    fun verySmallPositive_returnsLessThanThreshold() {
        assertEquals("<0.01", formatProgressPercent(0.001))
        assertEquals("<0.01", formatProgressPercent(0.005))
        assertEquals("<0.01", formatProgressPercent(0.009))
    }

    @Test
    fun atOneHundredthBoundary_returnsTwoDecimals() {
        assertEquals("0.01", formatProgressPercent(0.01))
        assertEquals("0.02", formatProgressPercent(0.0214))
        assertEquals("0.10", formatProgressPercent(0.099))
        assertEquals("0.99", formatProgressPercent(0.99))
    }

    @Test
    fun belowOne_995_roundsToOneDecimal() {
        // Boundary artifact: 0.995 rounds up via "%.2f" to "1.00" — acceptable
        assertEquals("1.00", formatProgressPercent(0.995))
    }

    @Test
    fun atOnePercent_returnsOneDecimal() {
        assertEquals("1.0", formatProgressPercent(1.0))
        assertEquals("1.5", formatProgressPercent(1.5))
        assertEquals("9.0", formatProgressPercent(9.0))
        assertEquals("9.9", formatProgressPercent(9.94))
    }

    @Test
    fun belowTen_995_roundsToTen() {
        // Boundary artifact: 9.95 rounds via "%.1f" to "10.0" — acceptable, transient
        assertEquals("10.0", formatProgressPercent(9.95))
    }

    @Test
    fun atTenPercent_returnsInteger() {
        assertEquals("10", formatProgressPercent(10.0))
        assertEquals("50", formatProgressPercent(50.0))
        assertEquals("99", formatProgressPercent(99.99))
        assertEquals("100", formatProgressPercent(100.0))
    }
}

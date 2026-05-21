package cash.p.terminal.modules.paycore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCoreApiServiceErrorTest {

    @Test
    fun indicatesPayCoreAmountOutOfRange_amountLessThanLimit_returnsTrue() {
        val body = """{"error":"the amount in rubles is less than the specified limit"}"""
        assertTrue(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_amountMoreThanLimit_returnsTrue() {
        val body = """{"error":"the amount in rubles is more than the specified limit"}"""
        assertTrue(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_amountBelowMinimum_returnsTrue() {
        val body = """{"error":"amount is below the minimum"}"""
        assertTrue(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_amountAboveMaximum_returnsTrue() {
        val body = """{"error":"amount is above the maximum"}"""
        assertTrue(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_plainTextLimitMessage_returnsTrue() {
        val body = "the amount in rubles is less than the specified limit"
        assertTrue(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_unrelatedMessage_returnsFalse() {
        val body = """{"error":"internal server error"}"""
        assertFalse(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_mentionsLimitButNotAmount_returnsFalse() {
        val body = """{"error":"rate limit exceeded"}"""
        assertFalse(body.indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun indicatesPayCoreAmountOutOfRange_emptyBody_returnsFalse() {
        assertFalse("".indicatesPayCoreAmountOutOfRange())
    }

    @Test
    fun payCoreErrorMessage_jsonError_returnsErrorField() {
        val body = """{"error":"the amount in rubles is less than the specified limit"}"""
        assertEquals("the amount in rubles is less than the specified limit", body.payCoreErrorMessage())
    }

    @Test
    fun payCoreErrorMessage_plainText_returnsTrimmedBody() {
        val body = "  bad request  "
        assertEquals("bad request", body.payCoreErrorMessage())
    }

    @Test
    fun payCoreErrorMessage_emptyBody_returnsNull() {
        assertNull("".payCoreErrorMessage())
        assertNull("   ".payCoreErrorMessage())
    }
}

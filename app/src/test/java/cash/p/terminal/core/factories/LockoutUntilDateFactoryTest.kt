package cash.p.terminal.core.factories

import cash.p.terminal.modules.pin.core.LockoutUntilDateFactory
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Date

class LockoutUntilDateFactoryTest {

    private val currentDateProvider = mockk<ICurrentDateProvider>()
    private val factory = LockoutUntilDateFactory(currentDateProvider)
    private var lockoutTimeStamp = 1L
    private var uptime = 1L

    @Before
    fun setUp() {
        clearMocks(currentDateProvider)
        lockoutTimeStamp = 1L
        uptime = 1L
    }


    @Test
    fun testUnlockTime_0Min() {
        val currentDate = Date()
        every { currentDateProvider.currentDate } returns currentDate
        uptime = 4000L
        Assert.assertEquals(factory.lockoutUntilDate(5, lockoutTimeStamp, uptime), currentDate)
    }

    @Test
    fun testUnlockTime_5Min() {
        val date = Date()
        val date2 = Date()
        every { currentDateProvider.currentDate } returns date
        date2.time = date.time + 5 * 60 * 1000
        Assert.assertEquals(factory.lockoutUntilDate(5, lockoutTimeStamp, uptime), date2)
    }

    @Test
    fun testUnlockTime_10Min() {
        val date = Date()
        val date2 = Date()
        every { currentDateProvider.currentDate } returns date
        date2.time = date.time + 10 * 60 * 1000
        Assert.assertEquals(factory.lockoutUntilDate(6, lockoutTimeStamp, uptime), date2)
    }

    @Test
    fun testUnlockTime_15Min() {
        val date = Date()
        val date2 = Date()
        every { currentDateProvider.currentDate } returns date
        date2.time = date.time + 15 * 60 * 1000
        Assert.assertEquals(factory.lockoutUntilDate(7, lockoutTimeStamp, uptime), date2)
    }

    @Test
    fun testUnlockTime_30Min() {
        val date = Date()
        val date2 = Date()
        every { currentDateProvider.currentDate } returns date
        date2.time = date.time + 30 * 60 * 1000
        Assert.assertEquals(factory.lockoutUntilDate(8, lockoutTimeStamp, uptime), date2)
    }

    @Test
    fun testUnlockTime_MoreThan30Min() {
        val date = Date()
        val date2 = Date()
        every { currentDateProvider.currentDate } returns date
        date2.time = date.time + 30 * 60 * 1000
        Assert.assertEquals(factory.lockoutUntilDate(9, lockoutTimeStamp, uptime), date2)
    }

}

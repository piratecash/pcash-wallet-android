package cash.p.terminal.modules.pin

import cash.p.terminal.modules.pin.core.ILockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.LockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.modules.pin.core.UptimeProvider
import io.horizontalsystems.core.ILockoutStorage
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class LockoutManagerTest {

    private lateinit var storage: ILockoutStorage
    private lateinit var uptimeProvider: UptimeProvider
    private lateinit var lockoutUntilDateFactory: ILockoutUntilDateFactory
    private lateinit var lockoutManager: LockoutManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        storage = mockk(relaxed = true)
        uptimeProvider = mockk()
        lockoutUntilDateFactory = mockk()

        lockoutManager = LockoutManager(storage, uptimeProvider, lockoutUntilDateFactory)
    }

    @Test
    fun `didFailUnlock increments attempts from null`() {
        every { storage.failedAttempts } returns null
        every { uptimeProvider.uptime } returns 0L

        lockoutManager.didFailUnlock()

        verify { storage.failedAttempts = 1 }
    }

    @Test
    fun `didFailUnlock increments attempts below threshold without storing uptime`() {
        every { storage.failedAttempts } returns 2
        every { uptimeProvider.uptime } returns 0L

        lockoutManager.didFailUnlock()

        verify(exactly = 0) { storage.lockoutUptime = any() }
        verify { storage.failedAttempts = 3 }
    }

    @Test
    fun `didFailUnlock increments attempts and stores uptime at threshold`() {
        every { storage.failedAttempts } returns 4
        every { uptimeProvider.uptime } returns 123L

        lockoutManager.didFailUnlock()

        verify { storage.lockoutUptime = 123L }
        verify { storage.failedAttempts = 5 }
    }

    @Test
    fun `currentState unlocked without attempts`() {
        every { storage.failedAttempts } returns null

        assertEquals(LockoutState.Unlocked(null), lockoutManager.currentState)
    }

    @Test
    fun `currentState unlocked with attempts remaining`() {
        every { storage.failedAttempts } returns 2

        assertEquals(LockoutState.Unlocked(3), lockoutManager.currentState)
    }

    @Test
    fun `currentState locked uses factory`() {
        val failedAttempts = 5
        val lockoutUptime = 100L
        val currentUptime = 120L
        val unlockDate = Date(currentUptime + 5_000)

        every { storage.failedAttempts } returns failedAttempts
        every { storage.lockoutUptime } returns lockoutUptime
        every { uptimeProvider.uptime } returns currentUptime
        every {
            lockoutUntilDateFactory.lockoutUntilDate(failedAttempts, lockoutUptime, currentUptime)
        } returns unlockDate

        assertEquals(LockoutState.Locked(unlockDate), lockoutManager.currentState)
    }

    @Test
    fun `currentState locked calculates uptime when storage empty`() {
        val failedAttempts = 6
        val currentUptime = 200L
        val unlockDate = Date(currentUptime + 10_000)

        every { storage.failedAttempts } returns failedAttempts
        every { storage.lockoutUptime } returns null
        every { uptimeProvider.uptime } returns currentUptime
        every {
            lockoutUntilDateFactory.lockoutUntilDate(failedAttempts, currentUptime, currentUptime)
        } returns unlockDate

        assertEquals(LockoutState.Locked(unlockDate), lockoutManager.currentState)
    }

    @Test
    fun `currentState unlocked when factory returns null`() {
        val failedAttempts = 6
        val currentUptime = 250L

        every { storage.failedAttempts } returns failedAttempts
        every { storage.lockoutUptime } returns null
        every { uptimeProvider.uptime } returns currentUptime
        every {
            lockoutUntilDateFactory.lockoutUntilDate(failedAttempts, currentUptime, currentUptime)
        } returns null

        assertEquals(LockoutState.Unlocked(1), lockoutManager.currentState)
    }

    @Test
    fun `dropFailedAttempts clears storage`() {
        lockoutManager.dropFailedAttempts()

        verify { storage.failedAttempts = null }
    }
}

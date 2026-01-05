package cash.p.terminal.modules.pin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinDbStorageHiddenLevelTest {

    private val pinDao = InMemoryPinDao()
    private val storage = PinDbStorage(pinDao)

    @Before
    fun setup() {
        pinDao.reset()
    }

    @Test
    fun `first hidden level starts at minus one`() {
        assertEquals(-1, storage.getNextHiddenWalletLevel())
    }

    @Test
    fun `next hidden level decrements from lowest existing`() {
        storage.store("regular", level = 0)
        storage.store("hidden-1", level = -1)
        storage.store("hidden-2", level = -3)

        assertEquals(-4, storage.getNextHiddenWalletLevel())
    }

    @Test
    fun `deleting hidden wallet PIN should not affect level 0 PIN`() {
        // Setup: Create regular PIN at level 0 and hidden wallet PIN at level -1
        storage.store("regular-pin", level = 0)
        storage.store("hidden-pin", level = -1)

        // Verify both PINs exist
        assertEquals(0, storage.getLevel("regular-pin"))
        assertEquals(-1, storage.getLevel("hidden-pin"))

        // Delete hidden wallet PIN (level -1)
        pinDao.deleteForLevel(-1)

        // EXPECTATION: Level 0 PIN should still exist
        assertEquals(0, storage.getLevel("regular-pin"))
        assertTrue(storage.isPinSetForLevel(0))

        // Hidden wallet PIN should be deleted
        assertEquals(null, storage.getLevel("hidden-pin"))
        assertFalse(storage.isPinSetForLevel(-1))
    }

    @Test
    fun `deleting one hidden wallet PIN should not affect other hidden wallet PINs`() {
        // Setup: Create regular PIN and multiple hidden wallet PINs
        storage.store("regular-pin", level = 0)
        storage.store("hidden-pin-1", level = -1)
        storage.store("hidden-pin-2", level = -2)
        storage.store("hidden-pin-3", level = -3)

        // Delete hidden wallet PIN at level -2
        pinDao.deleteForLevel(-2)

        // EXPECTATION: Other PINs should remain intact
        assertEquals(0, storage.getLevel("regular-pin"))
        assertEquals(-1, storage.getLevel("hidden-pin-1"))
        assertEquals(-3, storage.getLevel("hidden-pin-3"))

        // Only level -2 should be deleted
        assertEquals(null, storage.getLevel("hidden-pin-2"))
        assertTrue(storage.isPinSetForLevel(0))
        assertTrue(storage.isPinSetForLevel(-1))
        assertFalse(storage.isPinSetForLevel(-2))
        assertTrue(storage.isPinSetForLevel(-3))
    }

    @Test
    fun `deleting level 0 PIN should not affect hidden wallet PINs`() {
        // Setup: Create regular PIN and hidden wallet PINs
        storage.store("regular-pin", level = 0)
        storage.store("hidden-pin-1", level = -1)
        storage.store("hidden-pin-2", level = -2)

        // Clear level 0 PIN (simulate disabling regular PIN)
        storage.clearPasscode(0)

        // EXPECTATION: Hidden wallet PINs should remain intact
        assertEquals(-1, storage.getLevel("hidden-pin-1"))
        assertEquals(-2, storage.getLevel("hidden-pin-2"))
        assertTrue(storage.isPinSetForLevel(-1))
        assertTrue(storage.isPinSetForLevel(-2))

        // Level 0 PIN should be cleared (passcode null but record exists)
        assertEquals(null, storage.getLevel("regular-pin"))
        assertFalse(storage.isPinSetForLevel(0))
    }
}

private class InMemoryPinDao : PinDao {
    private val pins = sortedMapOf<Int, Pin>()

    fun reset() = pins.clear()

    override fun insert(pin: Pin) {
        pins[pin.level] = pin
    }

    override fun get(level: Int): Pin? = pins[level]

    override fun getAll(): List<Pin> = pins.values.toList()

    override fun getLastLevelPin(): Pin? = pins.values
        .filter { it.level != PinLevels.SECURE_RESET }
        .maxByOrNull { it.level }

    override fun deleteAllFromLevel(level: Int) {
        val iterator = pins.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key >= level) {
                iterator.remove()
            }
        }
    }

    override fun deleteForLevel(level: Int) {
        pins.remove(level)
    }

    override fun deleteUserLevelsFromLevel(level: Int) {
        val iterator = pins.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next().key
            if (key >= level && key < PinLevels.SECURE_RESET) {
                iterator.remove()
            }
        }
    }

    override fun deleteLogLoggingPinsFromLevel(logLoggingLevel: Int) {
        val iterator = pins.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next().key
            if (key >= logLoggingLevel) {
                iterator.remove()
            }
        }
    }

    override fun getMinLevel(): Int? = pins.keys.minOrNull()
}

package cash.p.terminal.modules.pin.core

import org.junit.Assert.assertEquals
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
}

private class InMemoryPinDao : PinDao {
    private val pins = sortedMapOf<Int, Pin>()

    fun reset() = pins.clear()

    override fun insert(pin: Pin) {
        pins[pin.level] = pin
    }

    override fun get(level: Int): Pin? = pins[level]

    override fun getAll(): List<Pin> = pins.values.toList()

    override fun getLastLevelPin(): Pin? = pins.values.maxByOrNull { it.level }

    override fun deleteAllFromLevel(level: Int) {
        val iterator = pins.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key >= level) {
                iterator.remove()
            }
        }
    }

    override fun getMinLevel(): Int? = pins.keys.minOrNull()
}

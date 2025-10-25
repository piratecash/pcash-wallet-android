package cash.p.terminal.modules.pin

import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.modules.pin.core.Pin
import cash.p.terminal.modules.pin.core.PinDao
import cash.p.terminal.modules.pin.core.PinDbStorage
import cash.p.terminal.modules.pin.core.PinManager
import cash.p.terminal.domain.usecase.ResetUseCase
import cash.p.terminal.modules.TestDispatcherProvider
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.IPinSettingsStorage
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinComponentSecureResetLevelTest {

    private val dispatcher = StandardTestDispatcher()
    private val pinDao = InMemoryPinDao()
    private val pinDbStorage = PinDbStorage(pinDao)
    private val pinManager = PinManager(pinDbStorage)
    private val userManager = mockk<DefaultUserManager>(relaxed = true)
    private val pinSettingsStorage = mockk<IPinSettingsStorage>(relaxed = true)
    private val backgroundManager = mockk<BackgroundManager>(relaxed = true)
    private val resetUseCase = mockk<ResetUseCase>(relaxed = true)
    private var secureResetCalled = false
    private lateinit var pinComponent: PinComponent
    private var currentUserLevel = 0

    @Before
    fun setup() {
        pinDao.reset()
        currentUserLevel = 0

        // Mock App.localStorage
        mockkObject(App)
        every { App.localStorage } returns mockk<ILocalStorage>(relaxed = true)

        every { userManager.getUserLevel() } answers { currentUserLevel }
        every { userManager.setUserLevel(any()) } answers {
            currentUserLevel = firstArg()
            Unit
        }
        every { userManager.currentUserLevelFlow } returns MutableStateFlow(currentUserLevel)

        clearMocks(resetUseCase)
        secureResetCalled = false
        coEvery { resetUseCase.invoke() } answers {
            secureResetCalled = true
        }

        pinComponent = PinComponent(
            pinSettingsStorage = pinSettingsStorage,
            userManager = userManager,
            pinDbStorage = pinDbStorage,
            backgroundManager = backgroundManager,
            resetUseCase = resetUseCase,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            scope = TestScope()
        )
    }

    private fun setUserLevel(level: Int) {
        currentUserLevel = level
    }

    @Test
    fun `duress PIN skips SECURE_RESET_PIN_LEVEL when user level is 9999`() {
        // Set user level to 9999
        setUserLevel(9999)

        // Set duress PIN
        pinComponent.setDuressPin("1234")

        // Duress PIN should be at level 10001, not 10000
        val duressLevel = pinManager.getPinLevel("1234")
        assertNotEquals(PinComponent.SECURE_RESET_PIN_LEVEL, duressLevel)
        assertEquals(PinComponent.SECURE_RESET_PIN_LEVEL + 1, duressLevel)
    }

    @Test
    fun `duress PIN uses normal level when user level is not 9999`() {
        // Set user level to 0 (regular)
        setUserLevel(0)

        // Set duress PIN
        pinComponent.setDuressPin("5678")

        // Duress PIN should be at level 1
        val duressLevel = pinManager.getPinLevel("5678")
        assertEquals(1, duressLevel)
    }

    @Test
    fun `isDuressPinSet returns false when no duress PIN at correct level`() {
        setUserLevel(9999)

        // Manually set a PIN at level 10000 (reserved)
        pinManager.store("9999", PinComponent.SECURE_RESET_PIN_LEVEL)

        // isDuressPinSet should return false because duress should be at 10001
        assertFalse(pinComponent.isDuressPinSet())
    }

    @Test
    fun `isDuressPinSet returns true when duress PIN set at correct level`() {
        setUserLevel(9999)

        // Set duress PIN through component
        pinComponent.setDuressPin("1111")

        // Should return true
        assertTrue(pinComponent.isDuressPinSet())
    }

    @Test
    fun `secure reset PIN and duress PIN can coexist at level 9999`() {
        setUserLevel(9999)

        // Set secure reset PIN
        pinComponent.setSecureResetPin("0000")

        // Set duress PIN
        pinComponent.setDuressPin("1111")

        // Both should exist at different levels
        assertEquals(PinComponent.SECURE_RESET_PIN_LEVEL, pinManager.getPinLevel("0000"))
        assertEquals(PinComponent.SECURE_RESET_PIN_LEVEL + 1, pinManager.getPinLevel("1111"))
    }

    @Test
    fun `disableDuressPin removes PIN from correct level at 9999`() {
        setUserLevel(9999)

        // Set duress PIN
        pinComponent.setDuressPin("2222")
        assertTrue(pinComponent.isDuressPinSet())

        // Disable duress PIN
        pinComponent.disableDuressPin()

        // Should be disabled now
        assertFalse(pinComponent.isDuressPinSet())

        // PIN should not exist at level 10001
        assertEquals(null, pinManager.getPinLevel("2222"))
    }

    @Test
    fun `secure reset pin promotes to primary pin on unlock`() = runTest(dispatcher) {
        setUserLevel(0)
        pinComponent.setPin("3333")
        pinComponent.setSecureResetPin("4444")

        val unlocked = pinComponent.unlock("4444")

        assertTrue(unlocked)
        assertEquals(0, pinManager.getPinLevel("4444"))
        assertFalse(pinComponent.isSecureResetPinSet())
        assertEquals(0, currentUserLevel)
        assertTrue(secureResetCalled)
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

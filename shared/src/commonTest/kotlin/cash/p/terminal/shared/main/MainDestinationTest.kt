package cash.p.terminal.shared.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainDestinationTest {

    @Test
    fun entries_returnsNavigationOrder() {
        assertEquals(
            listOf(
                MainDestination.Balance,
                MainDestination.Transactions,
                MainDestination.Market,
                MainDestination.Settings,
            ),
            MainDestination.entries.toList(),
        )
    }

    @Test
    fun fromString_validAndUnknownValues_returnsDestinationOrNull() {
        assertEquals(MainDestination.Market, MainDestination.fromString("Market"))
        assertNull(MainDestination.fromString("Unknown"))
        assertNull(MainDestination.fromString(null))
    }
}

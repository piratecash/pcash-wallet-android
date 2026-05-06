package cash.p.terminal.featureStacking.ui.staking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackingTypeTest {

    @Test
    fun pcash_maxHoursUntilFirstAccrual_returns9() {
        assertEquals(9, StackingType.PCASH.maxHoursUntilFirstAccrual)
    }

    @Test
    fun cosanta_maxHoursUntilFirstAccrual_returns36() {
        assertEquals(36, StackingType.COSANTA.maxHoursUntilFirstAccrual)
    }
}

package cash.p.terminal.modules.multiswap.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class AllBridgeProviderTest {

    @Test
    fun subtractFee_nonZeroFee_reducesAmount() {
        assertEquals(
            BigDecimal("8.5"),
            AllBridgeProvider.subtractFee(BigDecimal.TEN, BigDecimal("1.5")),
        )
    }

    @Test
    fun subtractFee_feeExceedsAmount_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            AllBridgeProvider.subtractFee(BigDecimal.ONE, BigDecimal.TEN)
        }
    }
}

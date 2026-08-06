package cash.p.terminal.modules.multiswap.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SwapHelperTest {

    @Test
    fun requiredInput_amountInMaxPresent_returnsMaximum() {
        assertEquals(
            BigDecimal("1.2"),
            requiredInput(BigDecimal.ONE, BigDecimal("1.2")),
        )
    }

    @Test
    fun insufficientAllowanceCaution_allowanceBelowRequiredInput_returnsTypedCaution() {
        assertTrue(
            insufficientAllowanceCaution(
                allowance = BigDecimal.ONE,
                requiredInput = BigDecimal("1.2"),
            ) is InsufficientAllowanceCaution
        )
    }

    @Test
    fun insufficientAllowanceCaution_allowanceCoversRequiredInput_returnsNull() {
        assertNull(
            insufficientAllowanceCaution(
                allowance = BigDecimal("1.2"),
                requiredInput = BigDecimal("1.2"),
            )
        )
    }
}

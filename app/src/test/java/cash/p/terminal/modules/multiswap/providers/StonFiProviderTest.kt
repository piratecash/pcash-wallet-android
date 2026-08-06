package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.SwapAmountDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class StonFiProviderTest {

    @Test
    fun minimumAskUnits_exactOut_usesRequestedTargetInsteadOfSlippageMinimum() {
        val minimum = stonFiMinimumAskUnits(
            direction = SwapAmountDirection.Out,
            amount = BigDecimal("1"),
            tokenOutDecimals = 9,
            simulatedMinimum = "995000006",
        )

        assertEquals(BigInteger("1000000000"), minimum)
    }

    @Test
    fun minimumAskUnits_exactIn_usesSimulationSlippageMinimum() {
        val minimum = stonFiMinimumAskUnits(
            direction = SwapAmountDirection.In,
            amount = BigDecimal("1"),
            tokenOutDecimals = 9,
            simulatedMinimum = "995000006",
        )

        assertEquals(BigInteger("995000006"), minimum)
    }
}

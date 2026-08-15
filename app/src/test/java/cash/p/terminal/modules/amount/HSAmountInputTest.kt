package cash.p.terminal.modules.amount

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class HSAmountInputTest {
    @Test fun setExternalCoinAmount_currencyInput_switchesToCoinAndPreservesAmount() {
        val viewModel = AmountInputViewModel2("DASH", 8, 2, AmountInputType.CURRENCY)
        var switches = 0

        viewModel.setExternalCoinAmount(BigDecimal("0.26537240"), AmountInputType.CURRENCY) { switches++ }

        assertEquals(1, switches)
        assertEquals(BigDecimal("0.26537240"), viewModel.coinAmount)
        assertEquals("0.26537240", viewModel.getEnterAmount())
    }
}

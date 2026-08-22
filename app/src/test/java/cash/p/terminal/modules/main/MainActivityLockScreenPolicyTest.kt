package cash.p.terminal.modules.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLockScreenPolicyTest {

    @Test
    fun calculatorPauseProtection_externalActivityPrepared_securesSnapshot() {
        assertEquals(
            CalculatorPauseProtection.SecureSnapshot,
            calculatorPauseProtection(
                calculatorMode = true,
                pinSet = true,
                externalActivityLaunching = true,
            )
        )
    }

    @Test
    fun calculatorPauseProtection_regularPause_showsCalculator() {
        assertEquals(
            CalculatorPauseProtection.ShowCalculator,
            calculatorPauseProtection(
                calculatorMode = true,
                pinSet = true,
                externalActivityLaunching = false,
            )
        )
    }

    @Test
    fun calculatorPauseProtection_calculatorModeDisabled_doesNothing() {
        assertEquals(
            CalculatorPauseProtection.None,
            calculatorPauseProtection(
                calculatorMode = false,
                pinSet = true,
                externalActivityLaunching = true,
            )
        )
    }
}

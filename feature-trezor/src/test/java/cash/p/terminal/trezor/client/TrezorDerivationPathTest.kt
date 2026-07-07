package cash.p.terminal.trezor.client

import org.junit.Assert.assertEquals
import org.junit.Test

class TrezorDerivationPathTest {

    private val hardened = 0x80000000.toInt()

    @Test
    fun parse_hardenedAndNonHardenedSegments_setsHighBitOnlyForHardened() {
        assertEquals(
            listOf(84 or hardened, 0 or hardened, 0 or hardened, 0, 5),
            TrezorDerivationPath.parse("m/84'/0'/0'/0/5")
        )
    }

    @Test
    fun format_mixedSegments_marksHardenedWithApostrophe() {
        assertEquals(
            "m/44'/60'/0'/0/0",
            TrezorDerivationPath.format(listOf(44 or hardened, 60 or hardened, 0 or hardened, 0, 0))
        )
    }

    @Test
    fun parseThenFormat_defaultAccountPaths_roundTrips() {
        for (path in listOf("m/84'/0'/0'", "m/44'/145'/0'", "m/44'/60'/0'/0/0", "m/44'/501'/0'/0'", "m/44'/148'/0'")) {
            assertEquals(path, TrezorDerivationPath.format(TrezorDerivationPath.parse(path)))
        }
    }
}

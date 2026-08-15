package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashAddressDerivationTest {

    private val deriver = ZcashAddressDeriver()

    private val seed get() = AccountType.Mnemonic(PHRASE.split(" "), "").seed

    @Test
    fun deriveUnifiedAddressFromSeed_sdkVector_matchesCanonicalUnifiedAddress() = runBlocking {
        assertEquals(EXPECTED_UA, deriver.deriveUnifiedAddressFromSeed(seed))
    }

    @Test
    fun deriveUnifiedAddressFromUfvk_sdkVector_matchesCanonicalUnifiedAddress() = runBlocking {
        val ufvk = DerivationTool.getInstance()
            .deriveUnifiedFullViewingKeys(seed, ZcashNetwork.Mainnet, numberOfAccounts = 1)
            .first()
            .encoding

        assertEquals(EXPECTED_UA, deriver.deriveUnifiedAddressFromUfvk(ufvk))
    }

    @Test
    fun saplingReceiver_canonicalUnifiedAddress_matchesEncodedReceiver() = runBlocking {
        assertEquals(EXPECTED_SAPLING_RECEIVER, deriver.saplingReceiver(EXPECTED_UA))
    }

    @Test
    fun transparentReceiver_canonicalUnifiedAddress_matchesEncodedReceiver() = runBlocking {
        assertEquals(EXPECTED_TRANSPARENT_RECEIVER, deriver.transparentReceiver(EXPECTED_UA))
    }

    private companion object {
        /** SDK regression vector (`sdk-lib/src/androidTest/.../TransparentTest.kt`), holds no funds. */
        const val PHRASE =
            "deputy visa gentle among clean scout farm drive comfort patch skin salt ranch cool ramp" +
                " warrior drink narrow normal lunch behind salt deal person"

        @Suppress("MaxLineLength")
        const val EXPECTED_UA =
            "u1t23erzgkn7c6c2jn66rspl4m45lg8rn3f7mn7le4yxk7693wr7sgx472jn95s00x8kx3hct5ej4tf76k59dfhsd809t7mzt9ldzw8f5083fw4xqvxfshl9u7ed2wyv6ypmzny0px0nvszslr5kr7fgk2zgfnlycddzqak4adsqjdzp76y7fl0k4ygamjr43t6rpxsf6xql8g20rdk0h"

        const val EXPECTED_SAPLING_RECEIVER =
            "zs1yc4sgtfwwzz6xfsy2xsradzr6m4aypgxhfw2vcn3hatrh5ryqsr08sgpemlg39vdh9kfupx20py"

        const val EXPECTED_TRANSPARENT_RECEIVER = "t1WksXp7ci6XkPNkEHNkFfzQXbRpBCQw7kW"
    }
}

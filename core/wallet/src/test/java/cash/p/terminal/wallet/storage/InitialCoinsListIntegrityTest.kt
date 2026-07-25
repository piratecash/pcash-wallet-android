package cash.p.terminal.wallet.storage

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InitialCoinsListIntegrityTest {

    @Test
    fun initialCoinsList_committedAsset_isValidSql() {
        validateDumpSql(initialCoinsFile().readText())
    }
}

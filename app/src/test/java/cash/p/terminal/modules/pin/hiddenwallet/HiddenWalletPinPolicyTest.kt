package cash.p.terminal.modules.pin.hiddenwallet

import cash.p.terminal.wallet.IAccountsStorage
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenWalletPinPolicyTest {

    private val pinComponent = mockk<IPinComponent>()
    private val accountsStorage = mockk<IAccountsStorage>()
    private val policy = HiddenWalletPinPolicy(pinComponent, accountsStorage)

    @Test
    fun `allows new pin`() {
        every { pinComponent.getPinLevel("1234") } returns null

        assertTrue(policy.canUse("1234"))
    }

    @Test
    fun `blocks pin associated with regular level`() {
        every { pinComponent.getPinLevel("1234") } returns 0

        assertFalse(policy.canUse("1234"))
    }

    @Test
    fun `blocks pin associated with hidden level that has wallets`() {
        every { pinComponent.getPinLevel("1234") } returns -1
        every { accountsStorage.getWalletsCountByLevel(-1) } returns 2

        assertFalse(policy.canUse("1234"))
    }

    @Test
    fun `allows pin associated with hidden level without wallets`() {
        every { pinComponent.getPinLevel("1234") } returns -2
        every { accountsStorage.getWalletsCountByLevel(-2) } returns 0

        assertTrue(policy.canUse("1234"))
    }
}

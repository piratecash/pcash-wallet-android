package cash.p.terminal.core.providers

import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ZcashFallbackAddressProviderTest {

    private val deriver = mockk<ZcashAddressDeriver>()
    private val provider = ZcashFallbackAddressProvider(deriver)

    private val mnemonicAccountType = mockk<AccountType.Mnemonic> {
        every { seed } returns SEED
    }

    @Before
    fun setup() {
        coEvery { deriver.deriveUnifiedAddressFromSeed(SEED) } returns UNIFIED
        coEvery { deriver.deriveUnifiedAddressFromUfvk(UFVK) } returns UNIFIED
        coEvery { deriver.saplingReceiver(UNIFIED) } returns SAPLING
        coEvery { deriver.transparentReceiver(UNIFIED) } returns TRANSPARENT
    }

    private fun wallet(
        accountType: AccountType = mnemonicAccountType,
        tokenType: TokenType = TokenType.AddressSpecTyped(AddressSpecType.Unified),
        blockchainType: BlockchainType = BlockchainType.Zcash,
        hardwareKey: HardwarePublicKey? = null,
    ): Wallet {
        val account = mockk<Account> {
            every { type } returns accountType
        }
        val token = mockk<Token>(relaxed = true) {
            every { this@mockk.blockchainType } returns blockchainType
            every { type } returns tokenType
        }
        return mockk {
            every { this@mockk.account } returns account
            every { this@mockk.token } returns token
            every { hardwarePublicKey } returns hardwareKey
        }
    }

    @Test
    fun getAddress_nonZcashWallet_returnsNull() = runTest {
        assertNull(provider.getAddress(wallet(blockchainType = BlockchainType.Ton)))
    }

    @Test
    fun getAddress_mnemonicUnifiedToken_returnsUnifiedAddress() = runTest {
        assertEquals(UNIFIED, provider.getAddress(wallet()))
    }

    @Test
    fun getAddress_mnemonicShieldedToken_returnsSaplingReceiver() = runTest {
        val wallet = wallet(tokenType = TokenType.AddressSpecTyped(AddressSpecType.Shielded))

        assertEquals(SAPLING, provider.getAddress(wallet))
    }

    @Test
    fun getAddress_mnemonicTransparentToken_returnsTransparentReceiver() = runTest {
        val wallet = wallet(tokenType = TokenType.AddressSpecTyped(AddressSpecType.Transparent))

        assertEquals(TRANSPARENT, provider.getAddress(wallet))
    }

    @Test
    fun getAddress_nativeTokenType_returnsSaplingReceiver() = runTest {
        assertEquals(SAPLING, provider.getAddress(wallet(tokenType = TokenType.Native)))
    }

    @Test
    fun getAddress_ufvkAccount_derivesFromViewingKeyNotSeed() = runTest {
        val wallet = wallet(accountType = AccountType.ZCashUfvKey(UFVK))

        assertEquals(UNIFIED, provider.getAddress(wallet))
        coVerify(exactly = 0) { deriver.deriveUnifiedAddressFromSeed(any()) }
    }

    @Test
    fun getAddress_trezorAccount_derivesFromHardwarePublicKey() = runTest {
        val hardwareKey = mockk<HardwarePublicKey> {
            every { key } returns SecretString(UFVK)
        }
        val wallet = wallet(
            accountType = mockk<AccountType.TrezorDevice>(),
            hardwareKey = hardwareKey,
        )

        assertEquals(UNIFIED, provider.getAddress(wallet))
    }

    @Test
    fun getAddress_unsupportedTokenType_returnsNull() = runTest {
        assertNull(provider.getAddress(wallet(tokenType = TokenType.Eip20("0x0"))))
    }

    @Test
    fun getAddress_unsupportedAccountType_returnsNull() = runTest {
        assertNull(provider.getAddress(wallet(accountType = AccountType.EvmAddress("0x0"))))
    }

    @Test
    fun getAddress_deriverThrows_returnsNull() = runTest {
        coEvery { deriver.deriveUnifiedAddressFromSeed(SEED) } throws UnsatisfiedLinkError("no .so")

        assertNull(provider.getAddress(wallet()))
    }

    private companion object {
        val SEED = ByteArray(64) { it.toByte() }
        const val UFVK = "uview1testviewingkey"
        const val UNIFIED = "u1testunifiedaddress"
        const val SAPLING = "zs1testsaplingreceiver"
        const val TRANSPARENT = "t1testtransparentreceiver"
    }
}

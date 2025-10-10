package cash.p.terminal.wallet

import cash.p.terminal.wallet.AccountType.EvmAddress
import cash.p.terminal.wallet.AccountType.EvmPrivateKey
import cash.p.terminal.wallet.AccountType.HardwareCard
import cash.p.terminal.wallet.AccountType.Mnemonic
import cash.p.terminal.wallet.AccountType.MnemonicMonero
import cash.p.terminal.wallet.AccountType.StellarAddress
import cash.p.terminal.wallet.AccountType.TonAddress
import cash.p.terminal.wallet.AccountType.ZCashUfvKey
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountCapabilitiesTest {

    @Test
    fun supportsWalletConnect_filtersAccountTypes() {
        val supported = listOf(mnemonicType(), hardwareType(), evmPrivateKeyType())
        val unsupported = listOf(
            EvmAddress("0x01"),
            TonAddress("ton1"),
            StellarAddress("stellar1"),
            ZCashUfvKey("ufvk"),
        )

        supported.forEach { type ->
            assertTrue(type.supportsWalletConnect, "${type::class.simpleName} should support WalletConnect")
        }
        unsupported.forEach { type ->
            assertFalse(type.supportsWalletConnect, "${type::class.simpleName} should not support WalletConnect")
        }
    }

    @Test
    fun canBeDuplicated_respectsMnemonicOnlyPolicy() {
        assertTrue(accountOf(mnemonicType()).canBeDuplicated())
        assertTrue(accountOf(moneroMnemonicType()).canBeDuplicated())
        assertFalse(accountOf(hardwareType()).canBeDuplicated())
        assertFalse(accountOf(EvmAddress("0x02")).canBeDuplicated())
    }

    @Test
    fun supportsTonConnect_allowsHardwareAndMnemonic() {
        assertTrue(accountOf(mnemonicType()).supportsTonConnect())
        assertTrue(accountOf(hardwareType()).supportsTonConnect())
        assertFalse(accountOf(evmPrivateKeyType()).supportsTonConnect())
        assertFalse(accountOf(TonAddress("ton1")).supportsTonConnect())
    }

    private fun accountOf(type: AccountType) = Account(
        id = type::class.simpleName ?: "account",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0
    )

    private fun mnemonicType() = Mnemonic(validMnemonicWords, passphrase = "")

    private fun moneroMnemonicType() = MnemonicMonero(
        words = validMnemonicWords,
        password = "",
        height = 0,
        walletInnerName = "wallet"
    )

    private fun hardwareType() = HardwareCard(
        cardId = "card",
        backupCardsCount = 1,
        walletPublicKey = "pubKey",
        signedHashes = 0
    )

    private fun evmPrivateKeyType() = EvmPrivateKey(BigInteger.ONE)

    private val validMnemonicWords = listOf(
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "abandon",
        "about"
    )
}

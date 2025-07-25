package cash.p.terminal.modules.manageaccount.privatekeys

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.toStellarWallet
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.stellarkit.StellarKit
import java.math.BigInteger

class PrivateKeysViewModel(
    account: Account,
    evmBlockchainManager: EvmBlockchainManager,
) : ViewModel() {

    var viewState by mutableStateOf(PrivateKeysModule.ViewState())
        private set

    init {

        val ethereumPrivateKey = when (val accountType = account.type) {
            is AccountType.Mnemonic -> {
                val chain = evmBlockchainManager.getChain(BlockchainType.Ethereum)
                toHexString(Signer.privateKey(accountType.words, accountType.passphrase, chain))
            }
            is AccountType.EvmPrivateKey -> toHexString(accountType.key)
            else -> null
        }

        val hdExtendedKey = (account.type as? AccountType.HdExtendedKey)?.hdExtendedKey

        val bip32RootKey = if (account.type is AccountType.Mnemonic) {
            val seed = Mnemonic().toSeed((account.type as AccountType.Mnemonic).words, (account.type as AccountType.Mnemonic).passphrase)
            HDExtendedKey(seed, HDWallet.Purpose.BIP44)
        } else if (hdExtendedKey?.derivedType == HDExtendedKey.DerivedType.Master) {
            hdExtendedKey
        } else {
            null
        }

        var accountExtendedDisplayType = ShowExtendedKeyModule.DisplayKeyType.AccountPrivateKey(true)
        val accountExtendedPrivateKey = bip32RootKey
            ?: if (hdExtendedKey?.derivedType == HDExtendedKey.DerivedType.Account && !hdExtendedKey.isPublic) {
                accountExtendedDisplayType = ShowExtendedKeyModule.DisplayKeyType.AccountPrivateKey(false)
                hdExtendedKey
            } else {
                null
            }

        val stellarSecretKey = try {
            val stellarWallet = account.type.toStellarWallet()
            StellarKit.getSecretSeed(stellarWallet)
        } catch (e: Throwable) {
            null
        }

        viewState = PrivateKeysModule.ViewState(
            evmPrivateKey = ethereumPrivateKey,
            bip32RootKey = bip32RootKey?.let {
                PrivateKeysModule.ExtendedKey(it, ShowExtendedKeyModule.DisplayKeyType.Bip32RootKey)
            },
            accountExtendedPrivateKey = accountExtendedPrivateKey?.let {
                PrivateKeysModule.ExtendedKey(it, accountExtendedDisplayType)
            },
            stellarSecretKey = stellarSecretKey
        )
    }

    private fun toHexString(key: BigInteger): String {
        return key.toByteArray().let {
            if (it.size > 32) {
                it.copyOfRange(1, it.size)
            } else {
                it
            }.toRawHexString()
        }
    }
}

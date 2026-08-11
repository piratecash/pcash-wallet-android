package cash.p.terminal.core.managers

import cash.p.terminal.tangem.common.CustomXPubKeyAddressParser
import cash.p.terminal.tangem.domain.model.AddressBytesWithPublicKey
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain

/**
 * Resolves EVM addresses and builds [Signer] instances without starting
 * [io.horizontalsystems.ethereumkit.core.EthereumKit]. The single source of truth for hardware-wallet
 * EVM key resolution (native token type preferred, any key for the blockchain as fallback), reused by
 * [EvmKitManager], WalletConnect and premium subscription signing so they never disagree on the
 * address/key a hardware account signs with.
 */
class EvmSignerFactory(
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val trezorClient: ITrezorClient,
) {

    suspend fun resolveAddress(account: Account, blockchainType: BlockchainType, chain: Chain): Address? =
        when (val type = account.type) {
            is AccountType.Mnemonic -> Signer.address(type.seed, chain)
            is AccountType.EvmPrivateKey -> Signer.address(type.key)
            is AccountType.EvmAddress -> Address(type.address)
            is AccountType.HardwareCard, is AccountType.TrezorDevice -> hardwareAddress(account.id, blockchainType)
            else -> null
        }

    suspend fun createSigner(account: Account, blockchainType: BlockchainType, chain: Chain): Signer? =
        when (val type = account.type) {
            is AccountType.Mnemonic -> Signer.getInstance(type.seed, chain)
            is AccountType.EvmPrivateKey -> Signer.getInstance(type.key, chain)
            is AccountType.HardwareCard -> hardwareKey(
                account.id, blockchainType
            )?.let { (publicKey, addressWithPublicKey) ->
                HardwareWalletEvmSigner(
                    address = Address(addressWithPublicKey.addressBytes),
                    publicKey = publicKey,
                    chain = chain,
                    expectedPublicKeyBytes = addressWithPublicKey.publicKey
                )
            }

            is AccountType.TrezorDevice -> hardwareKey(
                account.id, blockchainType
            )?.let { (publicKey, addressWithPublicKey) ->
                TrezorEvmSigner(
                    address = Address(addressWithPublicKey.addressBytes),
                    chain = chain,
                    derivationPath = publicKey.derivationPath,
                    trezorClient = trezorClient
                )
            }

            else -> null // EvmAddress (watch) and unsupported account types have no signer
        }

    private suspend fun hardwareKey(
        accountId: String,
        blockchainType: BlockchainType
    ): Pair<HardwarePublicKey, AddressBytesWithPublicKey>? {
        // Prefer the native key, but fall back to any key stored for this blockchain. All EVM token
        // types share the same derivation and address, so an account that enabled only an ERC-20 token
        // (no native row) is still a valid EVM wallet and must resolve.
        val publicKey = hardwarePublicKeyStorage.getKey(accountId, blockchainType, TokenType.Native)
            ?: hardwarePublicKeyStorage.getKeyByBlockchain(accountId, blockchainType)
            ?: return null
        return publicKey to CustomXPubKeyAddressParser.parse(publicKey.key.value)
    }

    private suspend fun hardwareAddress(accountId: String, blockchainType: BlockchainType): Address? =
        hardwareKey(accountId, blockchainType)?.let { (_, addressWithPublicKey) ->
            Address(addressWithPublicKey.addressBytes)
        }
}

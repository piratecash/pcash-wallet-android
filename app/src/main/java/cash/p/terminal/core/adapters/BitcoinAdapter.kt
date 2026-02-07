package cash.p.terminal.core.adapters

import cash.p.terminal.core.App
import cash.p.terminal.core.IFeeRateProvider
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.derivation
import cash.p.terminal.core.purpose
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.UsedAddress
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BalanceInfo
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.BitcoinKit.NetworkType
import io.horizontalsystems.core.BackgroundManager
import kotlinx.coroutines.launch
import io.horizontalsystems.core.entities.BlockchainType

class BitcoinAdapter(
    override val kit: BitcoinKit,
    syncMode: BitcoinCore.SyncMode,
    backgroundManager: BackgroundManager,
    wallet: Wallet,
    feeRateProvider: IFeeRateProvider? = null
) : BitcoinBaseAdapter(kit, syncMode, backgroundManager, wallet, DISPLAY_CONFIRMATIONS_THRESHOLD, feeRateProvider = feeRateProvider),
    BitcoinKit.Listener {
    constructor(
        wallet: Wallet,
        syncMode: BitcoinCore.SyncMode,
        backgroundManager: BackgroundManager,
        derivation: TokenType.Derivation,
        feeRateProvider: IFeeRateProvider? = null
    ) : this(
        kit = createKit(wallet, syncMode, derivation),
        syncMode = syncMode,
        backgroundManager = backgroundManager,
        wallet = wallet,
        feeRateProvider = feeRateProvider
    )

    init {
        kit.listener = this
    }

    //
    // BitcoinKit Listener
    //

    override val explorerTitle: String
        get() = "blockchair.com"


    override fun getTransactionUrl(transactionHash: String): String =
        "https://blockchair.com/bitcoin/transaction/$transactionHash"

    override fun onBalanceUpdate(balance: BalanceInfo) {
        scope.launch {
            estimateFeeForMax()
            balanceUpdatedSubject.onNext(Unit)
        }
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        lastBlockUpdatedSubject.onNext(Unit)
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        setState(state)
    }

    override fun onTransactionsUpdate(
        inserted: List<TransactionInfo>,
        updated: List<TransactionInfo>
    ) {
        val records = mutableListOf<TransactionRecord>()

        for (info in inserted) {
            records.add(transactionRecord(info))
        }

        for (info in updated) {
            records.add(transactionRecord(info))
        }

        transactionRecordsSubject.onNext(records)
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        // ignored for now
    }

    override val blockchainType = BlockchainType.Bitcoin

    override fun usedAddresses(change: Boolean): List<UsedAddress> =
        kit.usedAddresses(change).map {
            UsedAddress(
                it.index,
                it.address,
                "https://blockchair.com/bitcoin/address/${it.address}"
            )
        }

    companion object {
        private const val KIT_CONFIRMATIONS_THRESHOLD = 1
        private const val DISPLAY_CONFIRMATIONS_THRESHOLD = 3

        private fun createKit(
            wallet: Wallet,
            syncMode: BitcoinCore.SyncMode,
            derivation: TokenType.Derivation
        ): BitcoinKit {
            val account = wallet.account

            when (val accountType = account.type) {
                is AccountType.HdExtendedKey -> {
                    return BitcoinKit(
                        context = App.instance,
                        extendedKey = accountType.hdExtendedKey,
                        purpose = derivation.purpose,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = NetworkType.MainNet,
                        confirmationsThreshold = KIT_CONFIRMATIONS_THRESHOLD
                    )
                }

                is AccountType.Mnemonic -> {
                    return BitcoinKit(
                        context = App.instance,
                        words = accountType.words,
                        passphrase = accountType.passphrase,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = NetworkType.MainNet,
                        confirmationsThreshold = KIT_CONFIRMATIONS_THRESHOLD,
                        purpose = derivation.purpose
                    )
                }

                is AccountType.BitcoinAddress -> {
                    return BitcoinKit(
                        context = App.instance,
                        watchAddress = accountType.address,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = NetworkType.MainNet,
                        confirmationsThreshold = KIT_CONFIRMATIONS_THRESHOLD
                    )
                }

                is AccountType.HardwareCard -> {
                    val hardwareWalletEcdaBitcoinSigner = buildHardwareWalletEcdaBitcoinSigner(
                        accountId = account.id,
                        blockchainType = wallet.token.blockchainType,
                        tokenType = wallet.token.type,
                    )
                    val hardwareWalletSchnorrSigner = buildHardwareWalletSchnorrBitcoinSigner(
                        accountId = account.id,
                        blockchainType = wallet.token.blockchainType,
                        tokenType = wallet.token.type,
                    )
                    return BitcoinKit(
                        context = App.instance,
                        extendedKey = wallet.getHDExtendedKey()!!,
                        purpose = derivation.purpose,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = NetworkType.MainNet,
                        confirmationsThreshold = KIT_CONFIRMATIONS_THRESHOLD,
                        iInputSigner = hardwareWalletEcdaBitcoinSigner,
                        iSchnorrInputSigner = hardwareWalletSchnorrSigner,
                    )
                }

                else -> throw UnsupportedAccountException()
            }

        }

        fun clear(walletId: String) {
            BitcoinKit.clear(App.instance, NetworkType.MainNet, walletId)
        }

        fun firstAddress(accountType: AccountType, tokenType: TokenType): String {
            when (accountType) {
                is AccountType.Mnemonic -> {
                    val seed = accountType.seed
                    val derivation = tokenType.derivation ?: throw IllegalArgumentException()

                    val address = BitcoinKit.firstAddress(
                        seed,
                        derivation.purpose,
                        NetworkType.MainNet
                    )

                    return address.stringValue
                }

                is AccountType.HdExtendedKey -> {
                    val key = accountType.hdExtendedKey
                    val derivation = tokenType.derivation ?: throw IllegalArgumentException()
                    val address = BitcoinKit.firstAddress(
                        key,
                        derivation.purpose,
                        NetworkType.MainNet
                    )

                    return address.stringValue
                }

                is AccountType.BitcoinAddress -> {
                    return accountType.address
                }

                is AccountType.HardwareCard,
                is AccountType.EvmAddress,
                is AccountType.EvmPrivateKey,
                is AccountType.MnemonicMonero,
                is AccountType.SolanaAddress,
                is AccountType.TonAddress,
                is AccountType.TronAddress,
                is AccountType.StellarAddress,
                is AccountType.StellarSecretKey,
                is AccountType.ZCashUfvKey -> throw UnsupportedAccountException()
            }

        }
    }
}

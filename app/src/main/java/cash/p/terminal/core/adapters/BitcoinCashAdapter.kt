package cash.p.terminal.core.adapters

import cash.p.terminal.core.App
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.core.kitCoinType
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincash.BitcoinCashKit.NetworkType
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BalanceInfo
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenType
import java.math.BigDecimal

class BitcoinCashAdapter(
    override val kit: BitcoinCashKit,
    syncMode: BitcoinCore.SyncMode,
    backgroundManager: BackgroundManager,
    wallet: Wallet,
) : BitcoinBaseAdapter(kit, syncMode, backgroundManager, wallet, confirmationsThreshold), BitcoinCashKit.Listener, ISendBitcoinAdapter {

    constructor(
        wallet: Wallet,
        syncMode: BitcoinCore.SyncMode,
        backgroundManager: BackgroundManager,
        addressType: TokenType.AddressType
    ) : this(createKit(wallet, syncMode, addressType), syncMode, backgroundManager, wallet)

    init {
        kit.listener = this
    }

    //
    // BitcoinBaseAdapter
    //

    override val satoshisInBitcoin: BigDecimal = BigDecimal.valueOf(Math.pow(10.0, decimal.toDouble()))

    //
    // BitcoinCashKit Listener
    //

    override val explorerTitle: String
        get() = "blockchair.com"

    override fun getTransactionUrl(transactionHash: String): String =
        "https://blockchair.com/bitcoin-cash/transaction/$transactionHash"

    override fun onBalanceUpdate(balance: BalanceInfo) {
        balanceUpdatedSubject.onNext(Unit)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        lastBlockUpdatedSubject.onNext(Unit)
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        setState(state)
    }

    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
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

    override val blockchainType = BlockchainType.BitcoinCash

    override fun usedAddresses(change: Boolean): List<UsedAddress> =
        kit.usedAddresses(change).map { UsedAddress(it.index, it.address, "https://blockchair.com/bitcoin-cash/address/${it.address}") }

    companion object {
        private const val confirmationsThreshold = 3

        private fun createKit(
            wallet: Wallet,
            syncMode: BitcoinCore.SyncMode,
            addressType: TokenType.AddressType
        ): BitcoinCashKit {
            val account = wallet.account
            val networkType = getNetworkType(addressType.kitCoinType)
            when (val accountType = account.type) {
                is AccountType.HdExtendedKey -> {
                    return BitcoinCashKit(
                        context = App.instance,
                        extendedKey = accountType.hdExtendedKey,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = networkType,
                        confirmationsThreshold = confirmationsThreshold
                    )
                }

                is AccountType.Mnemonic -> {
                    return BitcoinCashKit(
                        context = App.instance,
                        words = accountType.words,
                        passphrase = accountType.passphrase,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = networkType,
                        confirmationsThreshold = confirmationsThreshold
                    )
                }

                is AccountType.BitcoinAddress -> {
                    return BitcoinCashKit(
                        context = App.instance,
                        watchAddress = accountType.address,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = networkType,
                        confirmationsThreshold = confirmationsThreshold,
                    )
                }

                is AccountType.HardwareCard -> {
                    val hardwareWalletEcdaBitcoinSigner = buildHardwareWalletEcdaBitcoinSigner(
                        accountId = account.id,
                        blockchainType = wallet.token.blockchainType,
                        tokenType = wallet.token.type
                    )
                    val hardwareWalletSchnorrSigner = buildHardwareWalletSchnorrBitcoinSigner(
                        accountId = account.id,
                        blockchainType = wallet.token.blockchainType,
                        tokenType = wallet.token.type,
                    )
                    return BitcoinCashKit(
                        context = App.instance,
                        extendedKey = wallet.getHDExtendedKey()!!,
                        walletId = account.id,
                        syncMode = syncMode,
                        networkType = networkType,
                        confirmationsThreshold = confirmationsThreshold,
                        iInputSigner = hardwareWalletEcdaBitcoinSigner,
                        iSchnorrInputSigner = hardwareWalletSchnorrSigner
                    )
                }

                else -> throw UnsupportedAccountException()
            }

        }

        fun clear(walletId: String) {
            BitcoinCashKit.clear(App.instance, getNetworkType(), walletId)
        }

        private fun getNetworkType(kitCoinType: MainNetBitcoinCash.CoinType = MainNetBitcoinCash.CoinType.Type145) =
            NetworkType.MainNet(kitCoinType)

        fun firstAddress(accountType: AccountType, tokenType: TokenType) : String {
            val bitcoinCashCoinType = tokenType.bitcoinCashCoinType ?: throw IllegalArgumentException()

            val kitCoinType = when (bitcoinCashCoinType) {
                TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0
                TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145
            }

            val networkType = NetworkType.MainNet(kitCoinType)

            when (accountType) {
                is AccountType.Mnemonic -> {
                    val seed = accountType.seed

                    val address = BitcoinCashKit.firstAddress(
                        seed,
                        networkType
                    )

                    return address.stringValue
                }
                is AccountType.HdExtendedKey -> {
                    val key = accountType.hdExtendedKey
                    val address = BitcoinCashKit.firstAddress(
                        key,
                        networkType
                    )

                    return address.stringValue
                }
                is AccountType.BitcoinAddress -> {
                    return accountType.address
                }

                is AccountType.EvmAddress,
                is AccountType.EvmPrivateKey,
                is AccountType.HardwareCard,
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

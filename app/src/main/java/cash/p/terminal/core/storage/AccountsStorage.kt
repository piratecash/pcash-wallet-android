package cash.p.terminal.core.storage

import cash.p.terminal.entities.ActiveAccount
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.CexType
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.SecretList
import cash.p.terminal.wallet.entities.SecretString
import io.reactivex.Flowable

class AccountsStorage(appDatabase: AppDatabase) : IAccountsStorage {

    private val dao: AccountsDao by lazy {
        appDatabase.accountsDao()
    }

    companion object {
        // account type codes stored in db
        private const val MNEMONIC = "mnemonic"
        private const val PRIVATE_KEY = "private_key"
        private const val ADDRESS = "address"
        private const val SOLANA_ADDRESS = "solana_address"
        private const val TRON_ADDRESS = "tron_address"
        private const val TON_ADDRESS = "ton_address"
        private const val BITCOIN_ADDRESS = "bitcoin_address"
        private const val HD_EXTENDED_KEY = "hd_extended_key"
        private const val UFVK = "ufvk"
        private const val CEX = "cex"
    }

    override fun getActiveAccountId(level: Int): String? {
        return dao.getActiveAccount(level)?.accountId
    }

    override fun setActiveAccountId(level: Int, id: String?) {
        if (id == null) {
            dao.deleteActiveAccount(level)
        } else {
            dao.insertActiveAccount(ActiveAccount(level, id))
        }
    }

    override val isAccountsEmpty: Boolean
        get() = dao.getTotalCount() == 0

    override fun allAccounts(accountsMinLevel: Int): List<Account> {
        return dao.getAll(accountsMinLevel)
                .mapNotNull { record: AccountRecord ->
                    try {
                        val accountType = when (record.type) {
                            MNEMONIC -> AccountType.Mnemonic(record.words!!.list, record.passphrase?.value ?: "")
                            PRIVATE_KEY -> AccountType.EvmPrivateKey(record.key!!.value.toBigInteger())
                            ADDRESS -> AccountType.EvmAddress(record.key!!.value)
                            SOLANA_ADDRESS -> AccountType.SolanaAddress(record.key!!.value)
                            TRON_ADDRESS -> AccountType.TronAddress(record.key!!.value)
                            TON_ADDRESS -> AccountType.TonAddress(record.key!!.value)
                            BITCOIN_ADDRESS -> AccountType.BitcoinAddress.fromSerialized(record.key!!.value)
                            HD_EXTENDED_KEY -> AccountType.HdExtendedKey(record.key!!.value)
                            UFVK -> AccountType.ZCashUfvKey(record.key!!.value)
                            CEX -> {
                                CexType.deserialize(record.key!!.value)?.let {
                                    AccountType.Cex(it)
                                }
                            }
                            else -> null
                        }
                        Account(
                            id = record.id,
                            name = record.name,
                            type = accountType!!,
                            origin = AccountOrigin.valueOf(record.origin),
                            level = record.level,
                            isBackedUp = record.isBackedUp,
                            isFileBackedUp = record.isFileBackedUp
                        )
                    } catch (ex: Exception) {
                        null
                    }
                }
    }

    override fun getDeletedAccountIds(): List<String> {
        return dao.getDeletedIds()
    }

    override fun clearDeleted() {
        return dao.clearDeleted()
    }

    override fun save(account: Account) {
        dao.insert(getAccountRecord(account))
    }

    override fun update(account: Account) {
        dao.update(getAccountRecord(account))
    }

    override fun updateLevels(accountIds: List<String>, level: Int) {
        dao.updateLevels(accountIds, level)
    }

    override fun updateMaxLevel(level: Int) {
        dao.updateMaxLevel(level)
    }

    override fun delete(id: String) {
        dao.delete(id)
    }

    override fun getNonBackedUpCount(): Flowable<Int> {
        return dao.getNonBackedUpCount()
    }

    override fun clear() {
        dao.deleteAll()
    }

    private fun getAccountRecord(account: Account): AccountRecord {
        var words: SecretList? = null
        var passphrase: SecretString? = null
        var key: SecretString? = null
        val accountType: String

        when (account.type) {
            is AccountType.Mnemonic -> {
                words = SecretList((account.type as AccountType.Mnemonic).words)
                passphrase = SecretString((account.type as AccountType.Mnemonic).passphrase)
                accountType = MNEMONIC
            }
            is AccountType.EvmPrivateKey -> {
                key = SecretString((account.type as AccountType.EvmPrivateKey).key.toString())
                accountType = PRIVATE_KEY
            }
            is AccountType.EvmAddress -> {
                key = SecretString((account.type as AccountType.EvmAddress).address)
                accountType = ADDRESS
            }
            is AccountType.SolanaAddress -> {
                key = SecretString((account.type as AccountType.SolanaAddress).address)
                accountType = SOLANA_ADDRESS
            }
            is AccountType.TronAddress -> {
                key = SecretString((account.type as AccountType.TronAddress).address)
                accountType = TRON_ADDRESS
            }
            is AccountType.TonAddress -> {
                key = SecretString((account.type as AccountType.TonAddress).address)
                accountType = TON_ADDRESS
            }
            is AccountType.BitcoinAddress -> {
                key = SecretString((account.type as AccountType.BitcoinAddress).serialized)
                accountType = BITCOIN_ADDRESS
            }
            is AccountType.HdExtendedKey -> {
                key = SecretString((account.type as AccountType.HdExtendedKey).keySerialized)
                accountType = HD_EXTENDED_KEY
            }
            is AccountType.Cex -> {
                key = SecretString((account.type as AccountType.Cex).cexType.serialized())
                accountType = CEX
            }
            is AccountType.ZCashUfvKey -> {
                key = SecretString((account.type as AccountType.ZCashUfvKey).key)
                accountType = UFVK
            }
        }

        return AccountRecord(
            id = account.id,
            name = account.name,
            type = accountType,
            origin = account.origin.value,
            isBackedUp = account.isBackedUp,
            isFileBackedUp = account.isFileBackedUp,
            words = words,
            passphrase = passphrase,
            key = key,
            level = account.level
        )
    }

}

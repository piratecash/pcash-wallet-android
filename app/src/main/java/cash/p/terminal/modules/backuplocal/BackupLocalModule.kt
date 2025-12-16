package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.wallet.AccountType
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.hdwalletkit.Base58
import io.horizontalsystems.tronkit.toBigInteger

object BackupLocalModule {
    const val BACKUP_VERSION = 3
    const val BACKUP_VERSION_V4 = 4

    // V3 backup wrapper - entire content encrypted
    data class BackupV3(
        val version: Int,
        val encrypted: String // Base64 encoded BackupCrypto JSON
    )

    /**
     * V4 Binary format for more efficient storage.
     * File structure: [4B magic][1B version][container bytes]
     *
     * Benefits over JSON+Hex format:
     * - 50% smaller files (no hex encoding overhead)
     * - Faster parsing (no JSON/hex conversion)
     */
    object BackupV4Binary {
        // Magic bytes: "PW4B" (PCash Wallet V4 Binary)
        val MAGIC = byteArrayOf(0x50, 0x57, 0x34, 0x42)
        const val VERSION: Byte = 0x04
        const val HEADER_SIZE = 5 // 4 magic + 1 version

        /**
         * Creates binary backup from container bytes.
         * @param container Raw deniable encryption container
         * @return Complete binary backup file contents
         */
        fun create(container: ByteArray): ByteArray {
            val result = ByteArray(HEADER_SIZE + container.size)
            System.arraycopy(MAGIC, 0, result, 0, MAGIC.size)
            result[4] = VERSION
            System.arraycopy(container, 0, result, HEADER_SIZE, container.size)
            return result
        }

        /**
         * Checks if data starts with V4 binary magic bytes.
         */
        fun isBinaryFormat(data: ByteArray): Boolean {
            if (data.size < HEADER_SIZE) return false
            return data[0] == MAGIC[0] &&
                   data[1] == MAGIC[1] &&
                   data[2] == MAGIC[2] &&
                   data[3] == MAGIC[3]
        }

        /**
         * Extracts container bytes from binary backup.
         * @param data Complete binary backup file contents
         * @return Container bytes, or null if invalid format
         */
        fun extractContainer(data: ByteArray): ByteArray? {
            if (!isBinaryFormat(data)) return null
            if (data[4] != VERSION) return null
            return data.copyOfRange(HEADER_SIZE, data.size)
        }

        /**
         * Gets version byte from binary backup.
         */
        fun getVersion(data: ByteArray): Byte? {
            if (data.size < HEADER_SIZE) return null
            if (!isBinaryFormat(data)) return null
            return data[4]
        }
    }

    private const val MNEMONIC = "mnemonic"
    private const val MNEMONIC_MONERO = "mnemonic_monero"
    private const val PRIVATE_KEY = "private_key"
    private const val SECRET_KEY = "secret_key"
    private const val ADDRESS = "evm_address"
    private const val SOLANA_ADDRESS = "solana_address"
    private const val TRON_ADDRESS = "tron_address"
    private const val TON_ADDRESS = "ton_address"
    private const val STELLAR_ADDRESS = "stellar_address"
    private const val BITCOIN_ADDRESS = "bitcoin_address"
    private const val HD_EXTENDED_KEY = "hd_extended_key"
    private const val UFVK = "ufvk"
    private const val HARDWARE_CARD = "hardware_card"

    //Backup Json file data structure

    data class WalletBackup(
        val crypto: BackupCrypto,
        val id: String,
        val type: String,
        @SerializedName("enabled_wallets")
        val enabledWallets: List<EnabledWalletBackup>?,
        @SerializedName("manual_backup")
        val manualBackup: Boolean,
        @SerializedName("file_backup")
        val fileBackup: Boolean,
        val timestamp: Long,
        val version: Int
    )

    data class BackupCrypto(
        val cipher: String,
        val cipherparams: CipherParams,
        val ciphertext: String,
        val kdf: String,
        val kdfparams: KdfParams,
        val mac: String
    )

    data class EnabledWalletBackup(
        @SerializedName("token_query_id")
        val tokenQueryId: String,
        @SerializedName("coin_name")
        val coinName: String? = null,
        @SerializedName("coin_code")
        val coinCode: String? = null,
        val decimals: Int? = null,
        val settings: Map<RestoreSettingType, String>?
    )

    data class CipherParams(
        val iv: String
    )

    class KdfParams(
        val dklen: Int,
        val n: Int,
        val p: Int,
        val r: Int,
        val salt: String
    )

    fun getAccountTypeString(accountType: AccountType): String = when (accountType) {
        is AccountType.Mnemonic -> MNEMONIC
        is AccountType.MnemonicMonero -> MNEMONIC_MONERO
        is AccountType.EvmPrivateKey -> PRIVATE_KEY
        is AccountType.StellarSecretKey -> SECRET_KEY
        is AccountType.EvmAddress -> ADDRESS
        is AccountType.SolanaAddress -> SOLANA_ADDRESS
        is AccountType.TronAddress -> TRON_ADDRESS
        is AccountType.TonAddress -> TON_ADDRESS
        is AccountType.StellarAddress -> STELLAR_ADDRESS
        is AccountType.BitcoinAddress -> BITCOIN_ADDRESS
        is AccountType.HdExtendedKey -> HD_EXTENDED_KEY
        is AccountType.ZCashUfvKey -> UFVK
        is AccountType.HardwareCard -> HARDWARE_CARD
    }

    @Throws(IllegalStateException::class)
    suspend fun getAccountTypeFromData(accountType: String, data: ByteArray): AccountType? {
        return when (accountType) {
            MNEMONIC -> {
                val parts = String(data, Charsets.UTF_8).split("@", limit = 2)
                //check for nonstandard mnemonic from iOs app
                if (parts[0].split("&").size > 1)
                    throw IllegalStateException("Non standard mnemonic")
                val words = parts[0].split(" ")
                val passphrase = if (parts.size > 1) parts[1] else ""
                AccountType.Mnemonic(words, passphrase)
            }

            MNEMONIC_MONERO -> {
                val parts = String(data, Charsets.UTF_8).split("@", limit = 3)
                if (parts.size != 2) {
                    throw IllegalStateException("Wrong monero backup format")
                }
                val words = parts[0].split(" ")
                val height = parts[1].toLong()
                val generateMoneroWalletUseCase: MoneroWalletUseCase = getKoinInstance()
                return generateMoneroWalletUseCase.restore(
                    words,
                    height,
                ) ?: throw IllegalStateException("Wallet is empty")
            }

            PRIVATE_KEY -> AccountType.EvmPrivateKey(data.toBigInteger())
            SECRET_KEY -> AccountType.StellarSecretKey(String(data, Charsets.UTF_8))
            ADDRESS -> AccountType.EvmAddress(String(data, Charsets.UTF_8))
            SOLANA_ADDRESS -> AccountType.SolanaAddress(String(data, Charsets.UTF_8))
            TRON_ADDRESS -> AccountType.TronAddress(String(data, Charsets.UTF_8))
            TON_ADDRESS -> AccountType.TonAddress(String(data, Charsets.UTF_8))
            STELLAR_ADDRESS -> AccountType.StellarAddress(String(data, Charsets.UTF_8))
            BITCOIN_ADDRESS -> AccountType.BitcoinAddress.fromSerialized(
                String(
                    data,
                    Charsets.UTF_8
                )
            )

            HD_EXTENDED_KEY -> AccountType.HdExtendedKey(Base58.encode(data))
            UFVK -> AccountType.ZCashUfvKey(String(data, Charsets.UTF_8))
            HARDWARE_CARD -> null

            else -> throw IllegalStateException("Unknown account type")
        }
    }

    fun getDataForEncryption(accountType: AccountType): ByteArray? = when (accountType) {
        is AccountType.Mnemonic -> {
            val passphrasePart = if (accountType.passphrase.isNotBlank()) {
                "@" + accountType.passphrase
            } else {
                ""
            }
            val combined = accountType.words.joinToString(" ") + passphrasePart
            combined.toByteArray(Charsets.UTF_8)
        }

        is AccountType.MnemonicMonero -> {
            val combined = listOf(
                accountType.words.joinToString(" "),
                accountType.height.toString()
            ).joinToString("@")
            combined.toByteArray(Charsets.UTF_8)
        }

        is AccountType.EvmPrivateKey -> accountType.key.toByteArray()
        is AccountType.StellarSecretKey -> accountType.key.toByteArray(Charsets.UTF_8)
        is AccountType.EvmAddress -> accountType.address.toByteArray(Charsets.UTF_8)
        is AccountType.SolanaAddress -> accountType.address.toByteArray(Charsets.UTF_8)
        is AccountType.TronAddress -> accountType.address.toByteArray(Charsets.UTF_8)
        is AccountType.TonAddress -> accountType.address.toByteArray(Charsets.UTF_8)
        is AccountType.StellarAddress -> accountType.address.toByteArray(Charsets.UTF_8)
        is AccountType.BitcoinAddress -> accountType.serialized.toByteArray(Charsets.UTF_8)
        is AccountType.HdExtendedKey -> Base58.decode(accountType.keySerialized)
        is AccountType.ZCashUfvKey -> accountType.key.toByteArray(Charsets.UTF_8)
        is AccountType.HardwareCard -> null
    }

    val kdfDefault = KdfParams(
        dklen = 32,
        n = 16384,
        p = 4,
        r = 8,
        salt = AppConfigProvider.accountsBackupFileSalt
    )
}
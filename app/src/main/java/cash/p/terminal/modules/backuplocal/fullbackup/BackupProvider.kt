package cash.p.terminal.modules.backuplocal.fullbackup

import android.util.Log
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EncryptDecryptManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.managers.MarketFavoritesManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.chart.ChartIndicatorSettingsDao
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.settings.appearance.AppIcon
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.core.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class BackupFileValidator {
    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .create()
    }

    fun validate(json: String) {
        val fullBackup = gson.fromJson(json, FullBackup::class.java)
        val walletBackup = gson.fromJson(json, BackupLocalModule.WalletBackup::class.java)

        val isSingleWalletBackup = fullBackup.settings == null && walletBackup.crypto != null && walletBackup.type != null && walletBackup.version in 1..2
        val isFullBackup = fullBackup.settings != null && fullBackup.version == 2 && walletBackup.crypto == null && walletBackup.type == null

        if (!isSingleWalletBackup && !isFullBackup) {
            throw Exception("Invalid json format")
        }
    }
}


class BackupProvider(
    private val localStorage: ILocalStorage,
    private val languageManager: LanguageManager,
    private val walletStorage: IEnabledWalletStorage,
    private val settingsManager: RestoreSettingsManager,
    private val accountManager: cash.p.terminal.wallet.IAccountManager,
    private val accountFactory: IAccountFactory,
    private val walletManager: IWalletManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val blockchainSettingsStorage: BlockchainSettingsStorage,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val marketFavoritesManager: MarketFavoritesManager,
    private val balanceViewTypeManager: BalanceViewTypeManager,
    private val appIconService: AppIconService,
    private val themeService: ThemeService,
    private val chartIndicatorManager: ChartIndicatorManager,
    private val chartIndicatorSettingsDao: ChartIndicatorSettingsDao,
    private val balanceHiddenManager: BalanceHiddenManager,
    private val baseTokenManager: BaseTokenManager,
    private val launchScreenService: LaunchScreenService,
    private val currencyManager: CurrencyManager,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val evmSyncSourceManager: EvmSyncSourceManager,
    private val evmSyncSourceStorage: EvmSyncSourceStorage,
    private val solanaRpcSourceManager: SolanaRpcSourceManager,
    private val contactsRepository: ContactsRepository
) {
    private val encryptDecryptManager by lazy { EncryptDecryptManager() }
    private val version = 2

    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .create()
    }

    private fun decrypted(crypto: BackupLocalModule.BackupCrypto, passphrase: String): ByteArray {
        val kdfParams = crypto.kdfparams
        val key = EncryptDecryptManager.getKey(passphrase, kdfParams) ?: throw RestoreException.EncryptionKeyException

        if (EncryptDecryptManager.passwordIsCorrect(crypto.mac, crypto.ciphertext, key)) {
            return encryptDecryptManager.decrypt(crypto.ciphertext, key, crypto.cipherparams.iv)
        } else {
            throw RestoreException.InvalidPasswordException
        }
    }

    @Throws
    suspend fun accountType(backup: BackupLocalModule.WalletBackup, passphrase: String): AccountType {
        val decrypted = decrypted(backup.crypto, passphrase)
        return BackupLocalModule.getAccountTypeFromData(backup.type, decrypted)
    }

    fun restoreCexAccount(accountType: AccountType, accountName: String) {
        val account = accountFactory.account(accountName, accountType, AccountOrigin.Restored, true, true)
        accountManager.save(account)
    }

    fun restoreSingleWalletBackup(
        type: AccountType,
        accountName: String,
        backup: BackupLocalModule.WalletBackup
    ) {
        val account = accountFactory.account(accountName, type, AccountOrigin.Restored, backup.manualBackup, true)
        accountManager.save(account)

        val enabledWalletBackups = backup.enabledWallets ?: listOf()
        val enabledWallets = enabledWalletBackups.map {
            EnabledWallet(
                tokenQueryId = it.tokenQueryId,
                accountId = account.id,
                coinName = it.coinName,
                coinCode = it.coinCode,
                coinDecimals = it.decimals,
                coinImage = null
            )
        }
        walletManager.saveEnabledWallets(enabledWallets)

        enabledWalletBackups.forEach { enabledWalletBackup ->
            TokenQuery.fromId(enabledWalletBackup.tokenQueryId)?.let { tokenQuery ->
                if (!enabledWalletBackup.settings.isNullOrEmpty()) {
                    val restoreSettings = RestoreSettings()
                    enabledWalletBackup.settings.forEach { (restoreSettingType, value) ->
                        restoreSettings[restoreSettingType] = value
                    }
                    restoreSettingsManager.save(restoreSettings, account, tokenQuery.blockchainType)
                }
            }
        }
    }

    private fun restoreWallets(walletBackupItems: List<WalletBackupItem>) {
        val accounts = mutableListOf<Account>()
        val enabledWallets = mutableListOf<EnabledWallet>()

        walletBackupItems.forEach {
            val account = it.account
            val wallets = it.enabledWallets.map {
                EnabledWallet(
                    tokenQueryId = it.tokenQueryId,
                    accountId = account.id,
                    coinName = it.coinName,
                    coinCode = it.coinCode,
                    coinDecimals = it.decimals,
                    coinImage = null
                )
            }

            accounts.add(account)
            enabledWallets.addAll(wallets)

            it.enabledWallets.forEach { enabledWalletBackup ->
                TokenQuery.fromId(enabledWalletBackup.tokenQueryId)?.let { tokenQuery ->
                    if (!enabledWalletBackup.settings.isNullOrEmpty()) {
                        val restoreSettings = RestoreSettings()
                        enabledWalletBackup.settings.forEach { (restoreSettingType, value) ->
                            restoreSettings[restoreSettingType] = value
                        }
                        restoreSettingsManager.save(restoreSettings, account, tokenQuery.blockchainType)
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            accountManager.import(accounts)
            walletManager.saveEnabledWallets(enabledWallets)
        }
    }

    private suspend fun restoreSettings(settings: Settings, passphrase: String) {
        balanceViewTypeManager.setViewType(settings.balanceViewType)

        withContext(Dispatchers.Main) {
            try {
                themeService.setThemeType(settings.currentTheme)
                languageManager.currentLocaleTag = settings.language
            } catch (e: Exception) {
                Log.e("e", "theme type restore", e)
            }
        }

        restoreChartSettings(settings.chartIndicatorsEnabled, settings.chartIndicators)

        balanceHiddenManager.setBalanceAutoHidden(settings.balanceAutoHidden)

        settings.conversionTokenQueryId?.let { baseTokenManager.setBaseTokenQueryId(it) }

        launchScreenService.setLaunchScreen(settings.launchScreen)
        localStorage.marketsTabEnabled = settings.marketsTabEnabled
        localStorage.balanceTabButtonsEnabled = settings.balanceHideButtons ?: false
        localStorage.priceChangeInterval = settings.priceChangeMode ?: PriceChangeInterval.LAST_24H
        currencyManager.setBaseCurrencyCode(settings.baseCurrency)


        settings.btcModes.forEach { btcMode ->
            val blockchainType = BlockchainType.fromUid(btcMode.blockchainTypeId)

            val restoreMode = BtcRestoreMode.values().firstOrNull { it.raw == btcMode.restoreMode }
            restoreMode?.let { btcBlockchainManager.save(it, blockchainType) }

            val sortMode = TransactionDataSortMode.values().firstOrNull { it.raw == btcMode.sortMode }
            sortMode?.let { btcBlockchainManager.save(sortMode, blockchainType) }
        }

        settings.evmSyncSources.custom.forEach { syncSource ->
            val blockchainType = BlockchainType.fromUid(syncSource.blockchainTypeId)
            val auth = syncSource.auth?.let {
                val decryptedAuth = decrypted(it, passphrase)
                String(decryptedAuth, Charsets.UTF_8)
            }
            evmSyncSourceManager.saveSyncSource(blockchainType, syncSource.url, auth)
        }

        settings.evmSyncSources.selected.forEach { syncSource ->
            val blockchainType = BlockchainType.fromUid(syncSource.blockchainTypeId)
            blockchainSettingsStorage.save(syncSource.url, blockchainType)
        }

        settings.solanaSyncSource?.let {
            blockchainSettingsStorage.save(settings.solanaSyncSource.name, BlockchainType.Solana)
        }

        if (settings.appIcon != (localStorage.appIcon ?: AppIcon.Main).titleText) {
            AppIcon.fromTitle(settings.appIcon)?.let { appIconService.setAppIcon(it) }
        }
    }

    private fun restoreChartSettings(
        chartIndicatorsEnabled: Boolean,
        chartIndicators: ChartIndicators
    ) {
        if (chartIndicatorsEnabled) {
            chartIndicatorManager.enable()
        } else {
            chartIndicatorManager.disable()
        }

        val defaultChartSettings = ChartIndicatorSettingsDao.defaultData()

        val rsi = chartIndicators.rsi
        val rsiDefaults = defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.RSI }
        val rsiChartSettings = rsiDefaults.sortedBy { it.index }.take(rsi.size).map { default ->
            val imported = rsi[default.index - 1]
            default.copy(
                extraData = mapOf(
                    "period" to imported.period.toString()
                ),
                enabled = imported.enabled
            )
        }

        val ma = chartIndicators.ma
        val maDefaults = defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.MA }
        val maChartSettings = maDefaults.sortedBy { it.index }.take(ma.size).map { default ->
            val imported = ma[default.index - 1]
            default.copy(
                extraData = mapOf(
                    "period" to imported.period.toString(),
                    "maType" to imported.type.uppercase(),
                ),
                enabled = imported.enabled
            )
        }

        val macd = chartIndicators.macd
        val macdDefaults = defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.MACD }
        val macdChartSettings = macdDefaults.sortedBy { it.index }.take(macd.size).map { default ->
            val imported = macd[default.index - 1]
            default.copy(
                extraData = mapOf(
                    "fast" to imported.fast.toString(),
                    "slow" to imported.slow.toString(),
                    "signal" to imported.signal.toString(),
                ),
                enabled = imported.enabled
            )
        }

        chartIndicatorSettingsDao.insertAll(rsiChartSettings + maChartSettings + macdChartSettings)
    }

    @Throws
    suspend fun restoreFullBackup(fullBackup: DecryptedFullBackup, passphrase: String) {
        if (fullBackup.wallets.isNotEmpty()) {
            restoreWallets(fullBackup.wallets)
        }

        if (fullBackup.watchlist.isNotEmpty()) {
            marketFavoritesManager.addAll(fullBackup.watchlist)
        }

        restoreSettings(fullBackup.settings, passphrase)

        if (fullBackup.contacts.isNotEmpty()) {
            contactsRepository.restore(fullBackup.contacts)
        }
    }

    suspend fun decryptedFullBackup(fullBackup: FullBackup, passphrase: String): DecryptedFullBackup {
        val walletBackupItems = mutableListOf<WalletBackupItem>()

        fullBackup.wallets?.forEach { walletBackup2 ->
            val backup = walletBackup2.backup
            val type = accountType(backup, passphrase)
            val name = walletBackup2.name

            val account = if (type.isWatchAccountType) {
                accountFactory.watchAccount(name, type)
            } else {
                accountFactory.account(name, type, AccountOrigin.Restored, backup.manualBackup, backup.fileBackup)
            }

            walletBackupItems.add(
                WalletBackupItem(
                    account = account,
                    enabledWallets = backup.enabledWallets ?: listOf()
                )
            )
        }

        var contacts = listOf<Contact>()
        fullBackup.contacts?.let {
            val decrypted = decrypted(it, passphrase)
            val contactsBackupJson = String(decrypted, Charsets.UTF_8)

            contacts = contactsRepository.parseFromJson(contactsBackupJson)
        }

        return DecryptedFullBackup(
            wallets = walletBackupItems,
            watchlist = fullBackup.watchlist ?: listOf(),
            settings = fullBackup.settings,
            contacts = contacts
        )
    }

    private fun fullBackupItems(
        accounts: List<Account>,
        watchlist: List<String>,
        contacts: List<Contact>,
        customRpcsCount: Int?
    ): BackupItems {
        val nonWatchAccounts = accounts.filter { !it.isWatchAccount }.sortedBy { it.name.lowercase() }
        val watchAccounts = accounts.filter { it.isWatchAccount }
        return BackupItems(
            accounts = nonWatchAccounts,
            watchWallets = watchAccounts.ifEmpty { null }?.size,
            watchlist = watchlist.ifEmpty { null }?.size,
            contacts = contacts.ifEmpty { null }?.size,
            customRpc = customRpcsCount,
        )
    }

    fun fullBackupItems() =
        fullBackupItems(
            accounts = accountManager.accounts,
            watchlist = marketFavoritesManager.getAll().map { it.coinUid },
            contacts = contactsRepository.contacts,
            customRpcsCount = evmSyncSourceStorage.getAll().ifEmpty { null }?.size
        )

    fun fullBackupItems(decryptedFullBackup: DecryptedFullBackup) =
        fullBackupItems(
            accounts = decryptedFullBackup.wallets.map { it.account },
            watchlist = decryptedFullBackup.watchlist,
            contacts = decryptedFullBackup.contacts,
            customRpcsCount = decryptedFullBackup.settings.evmSyncSources.custom.ifEmpty { null }?.size
        )

    fun shouldShowReplaceWarning(decryptedFullBackup: DecryptedFullBackup?): Boolean {
        return decryptedFullBackup != null && decryptedFullBackup.contacts.isNotEmpty() && contactsRepository.contacts.isNotEmpty()
    }

    @Throws
    fun createWalletBackup(account: Account, passphrase: String): String {
        val backup = walletBackup(account, passphrase)
        return gson.toJson(backup)
    }

    @Throws
    fun createFullBackup(accountIds: List<String>, passphrase: String): String {
        val wallets = accountManager.accounts
            .filter { it.isWatchAccount || accountIds.contains(it.id) }
            .map {
                val accountBackup = walletBackup(it, passphrase)
                WalletBackup2(it.name, accountBackup)
            }

        val watchlist = marketFavoritesManager.getAll().map { it.coinUid }

        val btcModes = btcBlockchainManager.allBlockchains.map { blockchain ->
            val restoreMode = btcBlockchainManager.restoreMode(blockchain.type)
            val sortMode = btcBlockchainManager.transactionSortMode(blockchain.type)
            BtcMode(blockchain.uid, restoreMode.raw, sortMode.raw)
        }

        val selectedEvmSyncSources = evmBlockchainManager.allBlockchains.map { blockchain ->
            val syncSource = evmSyncSourceManager.getSyncSource(blockchain.type)
            EvmSyncSourceBackup(blockchain.uid, syncSource.uri.toString(), null)
        }

        val customEvmSyncSources = evmBlockchainManager.allBlockchains.map { blockchain ->
            val customEvmSyncSources = evmSyncSourceManager.customSyncSources(blockchain.type)
            customEvmSyncSources.map { syncSource ->
                val auth = syncSource.auth?.let { encrypted(it, passphrase) }
                EvmSyncSourceBackup(blockchain.uid, syncSource.uri.toString(), auth)
            }
        }.flatten()

        val evmSyncSources = EvmSyncSources(selected = selectedEvmSyncSources, custom = customEvmSyncSources)

        val solanaSyncSource = SolanaSyncSource(BlockchainType.Solana.uid, solanaRpcSourceManager.rpcSource.name)

        val chartIndicators = chartIndicators()

        val settings = Settings(
            balanceViewType = balanceViewTypeManager.balanceViewTypeFlow.value,
            appIcon = localStorage.appIcon?.titleText ?: AppIcon.Main.titleText,
            currentTheme = themeService.selectedTheme,
            chartIndicatorsEnabled = localStorage.chartIndicatorsEnabled,
            chartIndicators = chartIndicators,
            balanceAutoHidden = balanceHiddenManager.balanceAutoHidden,
            conversionTokenQueryId = baseTokenManager.token?.tokenQuery?.id,
            language = languageManager.currentLocaleTag,
            launchScreen = launchScreenService.selectedLaunchScreen,
            marketsTabEnabled = localStorage.marketsTabEnabled,
            balanceHideButtons = localStorage.balanceTabButtonsEnabled,
            baseCurrency = currencyManager.baseCurrency.code,
            btcModes = btcModes,
            priceChangeMode = localStorage.priceChangeInterval,
            evmSyncSources = evmSyncSources,
            solanaSyncSource = solanaSyncSource,
        )

        val contacts = if (contactsRepository.contacts.isNotEmpty())
            encrypted(contactsRepository.asJsonString, passphrase)
        else
            null

        val fullBackup = FullBackup(
            wallets = wallets.ifEmpty { null },
            watchlist = watchlist.ifEmpty { null },
            settings = settings,
            contacts = contacts,
            timestamp = System.currentTimeMillis() / 1000,
            version = version,
            id = UUID.randomUUID().toString()
        )

        return gson.toJson(fullBackup)
    }

    private fun chartIndicators(): ChartIndicators {
        val indicators = chartIndicatorSettingsDao.getAllBlocking()
        val rsi = indicators
            .filter { it.type == ChartIndicatorSetting.IndicatorType.RSI }
            .map { chartIndicatorSetting ->
                val data = chartIndicatorSetting.getTypedDataRsi()
                RsiBackup(
                    period = data.period,
                    enabled = chartIndicatorSetting.enabled
                )
            }
        val ma = indicators
            .filter { it.type == ChartIndicatorSetting.IndicatorType.MA }
            .map { chartIndicatorSetting ->
                val data = chartIndicatorSetting.getTypedDataMA()
                MaBackup(
                    type = data.maType.lowercase(),
                    period = data.period,
                    enabled = chartIndicatorSetting.enabled
                )
            }
        val macd = indicators
            .filter { it.type == ChartIndicatorSetting.IndicatorType.MACD }
            .map { chartIndicatorSetting ->
                val data = chartIndicatorSetting.getTypedDataMacd()
                MacdBackup(
                    fast = data.fast,
                    slow = data.slow,
                    signal = data.signal,
                    enabled = chartIndicatorSetting.enabled
                )
            }
        return ChartIndicators(rsi, ma, macd)
    }

    private fun getId(value: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-512")
        val digest = md.digest(value)
        return digest.toHexString()
    }

    private fun encrypted(data: String, passphrase: String): BackupLocalModule.BackupCrypto {
        val kdfParams = BackupLocalModule.kdfDefault
        val secretText = data.toByteArray(Charsets.UTF_8)
        val key = EncryptDecryptManager.getKey(passphrase, kdfParams) ?: throw Exception("Couldn't get encryption key")

        val iv = EncryptDecryptManager.generateRandomBytes(16).toHexString()
        val encrypted = encryptDecryptManager.encrypt(secretText, key, iv)
        val mac = EncryptDecryptManager.generateMac(key, encrypted.toByteArray())

        return BackupLocalModule.BackupCrypto(
            cipher = "aes-128-ctr",
            cipherparams = BackupLocalModule.CipherParams(iv),
            ciphertext = encrypted,
            kdf = "scrypt",
            kdfparams = kdfParams,
            mac = mac.toHexString()
        )
    }

    @Throws
    private fun walletBackup(account: Account, passphrase: String): BackupLocalModule.WalletBackup {
        val kdfParams = BackupLocalModule.kdfDefault
        val secretText = BackupLocalModule.getDataForEncryption(account.type)
        val id = getId(secretText)
        val key = EncryptDecryptManager.getKey(passphrase, kdfParams) ?: throw Exception("Couldn't get encryption key")

        val iv = EncryptDecryptManager.generateRandomBytes(16).toHexString()
        val encrypted = encryptDecryptManager.encrypt(secretText, key, iv)
        val mac = EncryptDecryptManager.generateMac(key, encrypted.toByteArray())

        val wallets = walletStorage.enabledWallets(account.id)

        val enabledWalletsBackup = wallets.mapNotNull {
            val tokenQuery = TokenQuery.fromId(it.tokenQueryId) ?: return@mapNotNull null
            val settings = settingsManager.settings(account, tokenQuery.blockchainType).values
            BackupLocalModule.EnabledWalletBackup(
                tokenQueryId = it.tokenQueryId,
                coinName = it.coinName,
                coinCode = it.coinCode,
                decimals = it.coinDecimals,
                settings = settings.ifEmpty { null }
            )
        }

        val crypto = BackupLocalModule.BackupCrypto(
            cipher = "aes-128-ctr",
            cipherparams = BackupLocalModule.CipherParams(iv),
            ciphertext = encrypted,
            kdf = "scrypt",
            kdfparams = kdfParams,
            mac = mac.toHexString()
        )

        return BackupLocalModule.WalletBackup(
            crypto = crypto,
            id = id,
            type = BackupLocalModule.getAccountTypeString(account.type),
            enabledWallets = enabledWalletsBackup,
            manualBackup = account.isBackedUp,
            fileBackup = account.isFileBackedUp,
            timestamp = System.currentTimeMillis() / 1000,
            version = version
        )
    }

}

data class WalletBackupItem(
    val account: Account,
    val enabledWallets: List<BackupLocalModule.EnabledWalletBackup>
)

data class DecryptedFullBackup(
    val wallets: List<WalletBackupItem>,
    val watchlist: List<String>,
    val settings: Settings,
    val contacts: List<Contact>
)

data class BackupItem(
    val title: String,
    val subtitle: String
)

data class BackupItems(
    val accounts: List<Account>,
    val watchWallets: Int?,
    val watchlist: Int?,
    val contacts: Int?,
    val customRpc: Int?,
)

data class WalletBackup2(
    val name: String,
    val backup: BackupLocalModule.WalletBackup
)

data class FullBackup(
    val wallets: List<WalletBackup2>?,
    val watchlist: List<String>?,
    val settings: Settings,
    val contacts: BackupLocalModule.BackupCrypto?,
    val timestamp: Long,
    val version: Int,
    val id: String
)

data class BtcMode(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    @SerializedName("restore_mode")
    val restoreMode: String,
    @SerializedName("sort_mode")
    val sortMode: String
)

data class EvmSyncSourceBackup(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    val url: String,
    val auth: BackupLocalModule.BackupCrypto?
)

data class EvmSyncSources(
    val selected: List<EvmSyncSourceBackup>,
    val custom: List<EvmSyncSourceBackup>
)

data class SolanaSyncSource(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    val name: String
)

data class RsiBackup(
    val period: Int,
    val enabled: Boolean
)

data class MaBackup(
    val type: String,
    val period: Int,
    val enabled: Boolean
)

data class MacdBackup(
    val fast: Int,
    val slow: Int,
    val signal: Int,
    val enabled: Boolean
)

data class ChartIndicators(
    val rsi: List<RsiBackup>,
    val ma: List<MaBackup>,
    val macd: List<MacdBackup>
)

data class Settings(
    @SerializedName("balance_primary_value")
    val balanceViewType: BalanceViewType,
    @SerializedName("app_icon")
    val appIcon: String,
    @SerializedName("theme_mode")
    val currentTheme: ThemeType,
    @SerializedName("indicators_shown")
    val chartIndicatorsEnabled: Boolean,
    @SerializedName("indicators")
    val chartIndicators: ChartIndicators,
    @SerializedName("balance_auto_hide")
    val balanceAutoHidden: Boolean,
    @SerializedName("conversion_token_query_id")
    val conversionTokenQueryId: String?,
    val language: String,
    @SerializedName("launch_screen")
    val launchScreen: LaunchPage,
    @SerializedName("show_market")
    val marketsTabEnabled: Boolean,
    @SerializedName("balance_hide_buttons")
    val balanceHideButtons: Boolean?,
    @SerializedName("currency")
    val baseCurrency: String,

    @SerializedName("btc_modes")
    val btcModes: List<BtcMode>,
    @SerializedName("price_change_mode")
    val priceChangeMode: PriceChangeInterval?,
    @SerializedName("evm_sync_sources")
    val evmSyncSources: EvmSyncSources,
    @SerializedName("solana_sync_source")
    val solanaSyncSource: SolanaSyncSource?
)

sealed class RestoreException(message: String) : Exception(message) {
    object EncryptionKeyException : RestoreException("Couldn't get key from passphrase.")
    object InvalidPasswordException : RestoreException(cash.p.terminal.strings.helpers.Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword))
}

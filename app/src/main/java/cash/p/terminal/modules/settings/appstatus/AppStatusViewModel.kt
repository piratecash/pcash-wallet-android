package cash.p.terminal.modules.settings.appstatus

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import cash.p.terminal.BuildConfig
import java.io.File
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.settings.appstatus.AppStatusModule.BlockContent
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class AppStatusViewModel(
    private val moneroKitManager: MoneroKitManager,
    private val stellarKitManager: StellarKitManager,
) : ViewModel() {

    private val systemInfoManager: ISystemInfoManager = App.systemInfoManager
    private val localStorage: ILocalStorage = App.localStorage
    private val accountManager: IAccountManager = App.accountManager
    private val walletManager: IWalletManager = App.walletManager
    private val adapterManager: IAdapterManager = App.adapterManager
    private val marketKit: MarketKitWrapper = App.marketKit
    private val evmBlockchainManager: EvmBlockchainManager = App.evmBlockchainManager
    private val tronKitManager: TronKitManager = App.tronKitManager
    private val tonKitManager: TonKitManager = App.tonKitManager
    private val solanaKitManager: SolanaKitManager = App.solanaKitManager
    private val btcBlockchainManager: BtcBlockchainManager = App.btcBlockchainManager

    private var appLogs: Map<String, Any> = emptyMap()
    private var shareFile: File? = null

    private val _uiState = MutableStateFlow(
        AppStatusModule.UiState(
            appStatusAsText = null,
            blockViewItems = emptyList(),
            loading = true,
        )
    )
    val uiState: StateFlow<AppStatusModule.UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appLogs = AppLog.getLog()

            val blockViewItems = listOf<AppStatusModule.BlockData>()
                .asSequence()
                .plus(getAppInfoBlock())
                .plus(getVersionHistoryBlock())
                .plus(getWalletsStatusBlock())
                .plus(getBlockchainStatusBlock())
                .plus(getMarketLastSyncTimestampsBlock())
                .plus(getAppLogBlocks())
                .toList()

            val appStatusAsText = formatMapToString(getStatusMap())

            // Pre-write the share file on IO thread
            appStatusAsText?.let { text ->
                try {
                    val file = File(App.instance.cacheDir, "app_status_report.txt")
                    file.writeText(text)
                    shareFile = file
                } catch (_: Exception) {
                    // File write failed, share will be unavailable
                }
            }

            _uiState.value = AppStatusModule.UiState(
                appStatusAsText = appStatusAsText,
                blockViewItems = blockViewItems,
                loading = false,
            )
        }
    }

    fun getShareFileUri(context: Context): Uri? {
        val file = shareFile ?: return null
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }

    private companion object {
        val bitcoinLikeChains =
            listOf(
                BlockchainType.Bitcoin,
                BlockchainType.BitcoinCash,
                BlockchainType.Dash,
                BlockchainType.Litecoin,
                BlockchainType.ECash,
                BlockchainType.Dogecoin,
                BlockchainType.Cosanta,
                BlockchainType.PirateCash,
            )
    }

    private fun getStatusMap(): LinkedHashMap<String, Any> {
        val status = LinkedHashMap<String, Any>()

        status["App Info"] = getAppInfo()
        status["Version History"] = getVersionHistory()
        status["Wallets Status"] = getWalletsStatus()
        status["Blockchain Status"] = getBlockchainStatus()
        status["App Log"] = appLogs
        status["Market Last Sync Timestamps"] = getMarketLastSyncTimestamps()

        return status
    }

    private fun getAppLogBlocks(): List<AppStatusModule.BlockData> {
        val blocks = mutableListOf<AppStatusModule.BlockData>()
        var sectionTitleNotSet = true
        appLogs.forEach { (key, value) ->
            val title = if (sectionTitleNotSet) "App Log" else null
            val map = mapOf("" to value)
            val content = formatMapToString(map)?.removePrefix(":")
                ?.removePrefix("\n")
                ?.trimEnd() ?: ""
            val item = AppStatusModule.BlockData(
                title = title,
                content = listOf(
                    BlockContent.Header(key.replaceFirstChar(Char::uppercase)),
                    BlockContent.Text(content),
                )
            )
            blocks.add(item)
            sectionTitleNotSet = false
        }
        return blocks
    }

    private fun getVersionHistory(): Map<String, Any> {
        val versions = LinkedHashMap<String, Date>()

        localStorage.appVersions.sortedBy { it.timestamp }.forEach { version ->
            versions[version.version] = Date(version.timestamp)
        }
        return versions
    }

    private fun getVersionHistoryBlock(): AppStatusModule.BlockData {
        val versions = mutableListOf<BlockContent.TitleValue>()
        localStorage.appVersions.sortedBy { it.timestamp }.forEach { version ->
            versions.add(
                BlockContent.TitleValue(
                    DateHelper.formatDate(Date(version.timestamp), "MMM d, yyyy, HH:mm"),
                    version.version,
                )
            )
        }

        return AppStatusModule.BlockData("Version History", versions)
    }

    private fun getWalletsStatus(): Map<String, Any> {
        val wallets = LinkedHashMap<String, Any>()

        for (account in accountManager.accounts) {
            val title = account.name

            wallets[title] = getAccountDetails(account)
        }
        return wallets
    }

    private fun getWalletsStatusBlock(): List<AppStatusModule.BlockData> {
        val walletBlocks = mutableListOf<AppStatusModule.BlockData>()

        accountManager.accounts.forEachIndexed { index, account ->
            val title = if (index == 0) "Wallet Status" else null
            val origin = getAccountOrigin(account)

            walletBlocks.add(
                AppStatusModule.BlockData(
                    title,
                    listOf(
                        BlockContent.TitleValue("Name", account.name),
                        BlockContent.TitleValue("Origin", origin),
                        BlockContent.TitleValue("Type", account.type.description),
                    )
                )
            )
        }
        return walletBlocks
    }

    private fun getBlockchainStatus(): Map<String, Any> {
        val blockchainStatus = LinkedHashMap<String, Any>()

        walletManager.activeWallets
            .filter { bitcoinLikeChains.contains(it.token.blockchainType) }
            .sortedBy { it.token.coin.name }
            .forEach { wallet ->
                (adapterManager.getAdapterForWalletOld(wallet) as? BitcoinBaseAdapter)?.let { adapter ->
                    val statusTitle =
                        "${wallet.token.coin.name}${wallet.badge?.let { "-$it" } ?: ""}"
                    val restoreMode = btcBlockchainManager.restoreMode(wallet.token.blockchainType)
                    val statusInfo = mutableMapOf<String, Any>("Sync Mode" to restoreMode.name)
                    statusInfo.putAll(adapter.statusInfo)
                    blockchainStatus[statusTitle] = statusInfo
                }
            }

        evmBlockchainManager.allBlockchains
            .forEach { blockchain ->
                evmBlockchainManager.getEvmKitManager(blockchain.type).statusInfo?.let { statusInfo ->
                    blockchainStatus[blockchain.name] = statusInfo
                }
            }

        tronKitManager.statusInfo?.let { statusInfo ->
            blockchainStatus["Tron"] = statusInfo
        }

        tonKitManager.statusInfo?.let { statusInfo ->
            blockchainStatus["Ton"] = statusInfo
        }

        stellarKitManager.statusInfo?.let { statusInfo ->
            blockchainStatus["Stellar"] = statusInfo
        }

        solanaKitManager.statusInfo?.let { statusInfo ->
            blockchainStatus["Solana"] = statusInfo
        }

        walletManager.activeWallets.firstOrNull { it.token.blockchainType == BlockchainType.Zcash }
            ?.let { wallet ->
                (adapterManager.getAdapterForWalletOld(wallet) as? ZcashAdapter)?.let { adapter ->
                    blockchainStatus["Zcash"] = adapter.statusInfo
                }
            }

        moneroKitManager.moneroKitWrapper?.statusInfo()?.let { statusInfo ->
            blockchainStatus["Monero"] = statusInfo
        }

        return blockchainStatus
    }

    private fun getBlockchainStatusBlock(): List<AppStatusModule.BlockData> {
        val blocks = mutableListOf<AppStatusModule.BlockData>()

        walletManager.activeWallets
            .filter { bitcoinLikeChains.contains(it.token.blockchainType) }
            .sortedBy { it.token.coin.name }
            .forEach {
                val wallet = it
                val title = if (blocks.isEmpty()) "Blockchain Status" else null
                val block = when (val adapter = adapterManager.getAdapterForWalletOld(wallet)) {
                    is BitcoinBaseAdapter -> {
                        val restoreMode =
                            btcBlockchainManager.restoreMode(wallet.token.blockchainType)
                        val statusInfo = mutableMapOf<String, Any>("Sync Mode" to restoreMode.name)
                        statusInfo.putAll(adapter.statusInfo)
                        getBlockchainInfoBlock(
                            title,
                            "${wallet.token.coin.name}${wallet.badge?.let { "-$it" } ?: ""}",
                            statusInfo
                        )
                    }

                    else -> null
                }
                block?.let { blocks.add(it) }
            }

        evmBlockchainManager.allBlockchains
            .forEach { blockchain ->
                evmBlockchainManager.getEvmKitManager(blockchain.type).statusInfo?.let { statusInfo ->
                    val title = if (blocks.isEmpty()) "Blockchain Status" else null
                    val block = getBlockchainInfoBlock(title, blockchain.name, statusInfo)
                    blocks.add(block)
                }
            }

        tronKitManager.statusInfo?.let { statusInfo ->
            val title = if (blocks.isEmpty()) "Blockchain Status" else null
            val block = getBlockchainInfoBlock(title, "Tron", statusInfo)
            blocks.add(block)
        }

        solanaKitManager.statusInfo?.let {
            val title = if (blocks.isEmpty()) "Blockchain Status" else null
            val block = getBlockchainInfoBlock(title, "Solana", it)
            blocks.add(block)
        }

        walletManager.activeWallets
            .mapNotNull { wallet ->
                adapterManager.getAdapterForWalletOld(wallet)?.let { adapter ->
                    if (adapter.statusInfo.isEmpty()) {
                        return@mapNotNull null
                    }
                    getBlockchainInfoBlock(
                        title = if (blocks.isEmpty()) "Blockchain Status" else null,
                        blockchain = "${wallet.token.blockchain.type.stringRepresentation}",
                        statusInfo = adapter.statusInfo
                    )
                }
            }
            .forEach {
                blocks.add(it)
            }

        return blocks
    }

    fun getBlockchainInfoBlock(
        title: String?,
        blockchain: String,
        statusInfo: Map<String, Any>
    ): AppStatusModule.BlockData {
        return AppStatusModule.BlockData(
            title,
            listOf(
                BlockContent.TitleValue("Blockchain", blockchain),
                BlockContent.Text(formatMapToString(statusInfo)?.trimEnd() ?: ""),
            )
        )
    }

    private fun getMarketLastSyncTimestamps(): Map<String, Any> {
        val syncInfo = marketKit.syncInfo()
        return buildMap {
            put("Coins Timestamp", syncInfo.coinsTimestamp ?: "")
            put("Blockchains Timestamp", syncInfo.blockchainsTimestamp ?: "")
            put("Tokens Timestamp", syncInfo.tokensTimestamp ?: "")
            syncInfo.coinsCount?.let { put("Coins Count", it) }
            syncInfo.blockchainsCount?.let { put("Blockchains Count", it) }
            syncInfo.tokensCount?.let { put("Tokens Count", it) }
            syncInfo.serverAvailable?.let { put("Server Available", if (it) "Yes" else "No") }
        }
    }

    private fun getMarketLastSyncTimestampsBlock(): AppStatusModule.BlockData {
        val syncInfo = marketKit.syncInfo()

        return AppStatusModule.BlockData(
            title = "Market Sync Info",
            content = buildList {
                add(BlockContent.TitleValue("Coins Timestamp", syncInfo.coinsTimestamp ?: ""))
                add(
                    BlockContent.TitleValue(
                        "Blockchains Timestamp",
                        syncInfo.blockchainsTimestamp ?: ""
                    )
                )
                add(BlockContent.TitleValue("Tokens Timestamp", syncInfo.tokensTimestamp ?: ""))
                syncInfo.coinsCount?.let {
                    add(
                        BlockContent.TitleValue(
                            "Coins Count",
                            it.toString()
                        )
                    )
                }
                syncInfo.blockchainsCount?.let {
                    add(
                        BlockContent.TitleValue(
                            "Blockchains Count",
                            it.toString()
                        )
                    )
                }
                syncInfo.tokensCount?.let {
                    add(
                        BlockContent.TitleValue(
                            "Tokens Count",
                            it.toString()
                        )
                    )
                }
                syncInfo.serverAvailable?.let {
                    add(BlockContent.TitleValue("Server Available", if (it) "Yes" else "No"))
                }
            }
        )
    }

    private fun getAppInfo(): Map<String, Any> {
        val appInfo = LinkedHashMap<String, Any>()
        appInfo["Current Time"] = Date()
        appInfo["App Version"] = systemInfoManager.appVersion
        appInfo["Device Model"] = systemInfoManager.deviceModel
        appInfo["OS Version"] = systemInfoManager.osVersion

        return appInfo
    }

    private fun getAppInfoBlock(): AppStatusModule.BlockData {
        return AppStatusModule.BlockData(
            title = "App Info",
            content = buildList {
                add(
                    BlockContent.TitleValue(
                        "Current Time",
                        DateHelper.formatDate(Date(), "MMM d, yyyy, HH:mm")
                    )
                )
                add(BlockContent.TitleValue("App Version", systemInfoManager.appVersion))
                add(BlockContent.TitleValue("Device Model", systemInfoManager.deviceModel))
                add(BlockContent.TitleValue("OS Version", systemInfoManager.osVersion))
                addAll(getDeviceClass(App.instance))
                add(
                    BlockContent.TitleValue(
                        "System pin required",
                        if (localStorage.isSystemPinRequired) "Yes" else "No"
                    )
                )
            }
        )
    }

    private fun getDeviceClass(context: Context): List<BlockContent.TitleValue> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = am.memoryClass
        val largeMemory = am.largeMemoryClass
        val isLowRam = am.isLowRamDevice
        val perf = tryOrNull { Build.VERSION.MEDIA_PERFORMANCE_CLASS }

        val classLevel = when {
            perf == null -> "Unknown"
            isLowRam || memory < 128 -> "Low"
            perf >= 33 || memory >= 256 -> "High"
            else -> "Medium"
        }
        return buildList {
            add(BlockContent.TitleValue("Memory", "${memory}MB (${largeMemory}MB)"))
            add(BlockContent.TitleValue("Performance Class", "$classLevel ($perf)"))
        }
    }

    private fun getAccountDetails(account: cash.p.terminal.wallet.Account): LinkedHashMap<String, Any> {
        val accountDetails = LinkedHashMap<String, Any>()

        accountDetails["Origin"] = getAccountOrigin(account)
        accountDetails["Type"] = account.type.description

        return accountDetails
    }

    private fun getAccountOrigin(account: cash.p.terminal.wallet.Account): String {
        return if (account.isWatchAccount) "Watched" else account.origin.value
    }

    private fun formatMapToString(status: Map<String, Any>?): String? {
        if (status == null) return null
        val list = convertNestedMapToStringList(status)
        return list.joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertNestedMapToStringList(
        map: Map<String, Any>,
        bullet: String = "",
        level: Int = 0,
    ): List<String> {
        val resultList = mutableListOf<String>()
        map.forEach { (key, value) ->
            val indent = "  ".repeat(level)
            when (value) {
                is Map<*, *> -> {
                    resultList.add("$indent$bullet$key:")
                    resultList.addAll(
                        convertNestedMapToStringList(
                            map = value as Map<String, Any>,
                            bullet = " - ",
                            level = level + 1
                        )
                    )
                    if (level < 2) resultList.add("")
                }

                is Date -> {
                    val date = DateHelper.formatDate(value, "MMM d, yyyy, HH:mm")
                    resultList.add("$indent$bullet$key: $date")
                }

                else -> resultList.add("$indent$bullet$key: $value")
            }
        }
        return resultList
    }

}

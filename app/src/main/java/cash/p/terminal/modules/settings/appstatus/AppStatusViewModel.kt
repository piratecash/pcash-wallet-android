package cash.p.terminal.modules.settings.appstatus

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BtcBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.EvmBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.MoneroBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.SolanaBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.StatusItem
import cash.p.terminal.modules.blockchainstatus.StatusSection
import cash.p.terminal.modules.blockchainstatus.StellarBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.TonBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.TronBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.ZcashBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.appendStatusSection
import cash.p.terminal.modules.settings.appstatus.AppStatusModule.BlockContent
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class AppStatusViewModel(
    private val context: Context,
    private val systemInfoManager: ISystemInfoManager,
    private val localStorage: ILocalStorage,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
    private val marketKit: MarketKitWrapper,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val moneroKitManager: MoneroKitManager,
    private val stellarKitManager: StellarKitManager,
    private val tronKitManager: TronKitManager,
    private val tonKitManager: TonKitManager,
    private val solanaKitManager: SolanaKitManager,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val checkPremiumUseCase: CheckPremiumUseCase
) : ViewModel() {

    private var appLogs: Map<String, Any> = emptyMap()
    private var shareFile: File? = null

    private val _uiState = MutableStateFlow(
        AppStatusModule.UiState(
            appStatusAsText = null,
            blockViewItems = emptyList(),
            blockchainStatusSections = emptyList(),
            loading = true,
        )
    )
    val uiState: StateFlow<AppStatusModule.UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appLogs = AppLog.getLog()
            val fullLogs = AppLog.getFullLog()

            val blockViewItems = listOf<AppStatusModule.BlockData>()
                .asSequence()
                .plus(getAppInfoBlock())
                .plus(getEnabledBlockchainsBlock())
                .plus(getVersionHistoryBlock())
                .plus(getWalletsStatusBlock())
                .plus(getMarketLastSyncTimestampsBlock())
                .plus(getAppLogBlocks())
                .toList()

            val blockchainStatusSections = getBlockchainStatusSections()

            // Truncated text for clipboard (last 500 log entries) to avoid TransactionTooLargeException
            val appStatusAsText = buildStatusText(appLogs, blockchainStatusSections)

            // Pre-write the share file with full logs on IO thread
            try {
                val fullStatusText = buildStatusText(fullLogs, blockchainStatusSections)
                val file = File(context.cacheDir, "app_status_report.txt")
                file.writeText(fullStatusText)
                shareFile = file
            } catch (_: Exception) {
                // File write failed, share will be unavailable
            }

            _uiState.value = AppStatusModule.UiState(
                appStatusAsText = appStatusAsText,
                blockViewItems = blockViewItems,
                blockchainStatusSections = blockchainStatusSections,
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

    private fun buildStatusText(
        logs: Map<String, Any>,
        blockchainStatusSections: List<StatusSection>
    ): String = buildString {
        formatMapToString(getStatusMap(logs))?.let { append(it) }
        if (blockchainStatusSections.isNotEmpty()) {
            appendLine()
            appendLine("Blockchain Status")
            blockchainStatusSections.forEach { appendStatusSection(it) }
        }
    }

    private fun getStatusMap(logs: Map<String, Any>): LinkedHashMap<String, Any> {
        val status = LinkedHashMap<String, Any>()

        status["App Info"] = getAppInfo()
        status["Enabled Blockchains"] = getEnabledBlockchains()
        status["Version History"] = getVersionHistory()
        status["Wallets Status"] = getWalletsStatus()
        status["App Log"] = logs
        status["Market Last Sync Timestamps"] = getMarketLastSyncTimestamps()

        return status
    }

    private fun getBlockchainStatusSections(): List<StatusSection> {
        val sections = mutableListOf<StatusSection>()

        // BTC-like chains — grouped by blockchain type
        btcBlockchainManager.blockchainTypes.forEach { blockchainType ->
            val hasActiveWallets =
                walletManager.activeWallets.any { it.token.blockchainType == blockchainType }
            if (!hasActiveWallets) return@forEach

            val blockchain = btcBlockchainManager.blockchain(blockchainType) ?: return@forEach
            collectProviderSections(
                BtcBlockchainStatusProvider(
                    blockchain,
                    btcBlockchainManager,
                    walletManager,
                    adapterManager
                ), sections
            )
        }

        // EVM chains
        evmBlockchainManager.allBlockchains.forEach { blockchain ->
            collectProviderSections(
                EvmBlockchainStatusProvider(blockchain, evmBlockchainManager),
                sections
            )
        }

        // Other chains
        collectProviderSections(TronBlockchainStatusProvider(tronKitManager), sections)
        collectProviderSections(TonBlockchainStatusProvider(tonKitManager), sections)
        collectProviderSections(StellarBlockchainStatusProvider(stellarKitManager), sections)
        collectProviderSections(SolanaBlockchainStatusProvider(solanaKitManager), sections)
        collectProviderSections(
            ZcashBlockchainStatusProvider(walletManager, adapterManager),
            sections
        )
        collectProviderSections(MoneroBlockchainStatusProvider(moneroKitManager), sections)

        return sections
    }

    private fun collectProviderSections(
        provider: BlockchainStatusProvider,
        into: MutableList<StatusSection>
    ) {
        val status = provider.getStatus()
        status.sections.forEachIndexed { index, section ->
            if (section.items.isEmpty()) return@forEachIndexed
            if (index == 0) {
                into.add(
                    section.copy(
                        items = listOf(
                            StatusItem.KeyValue(
                                "Kit Version",
                                provider.kitVersion
                            )
                        ) + section.items
                    )
                )
            } else {
                into.add(section)
            }
        }
        status.sharedSection?.let { into.add(it) }
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
        appInfo["App Version"] = systemInfoManager.appVersionFull
        appInfo["Git Branch"] = AppConfigProvider.appGitBranch
        systemInfoManager.getSigningCertFingerprint()?.let {
            appInfo["App Signature"] = it
        }
        appInfo["Device Model"] = systemInfoManager.deviceModel
        appInfo["OS Version"] = systemInfoManager.osVersion
        getDeviceClass(context).forEach { appInfo[it.title] = it.value }
        appInfo["System pin required"] = if (localStorage.isSystemPinRequired) "Yes" else "No"

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
                add(BlockContent.TitleValue("App Version", systemInfoManager.appVersionFull))
                add(BlockContent.TitleValue("Git Branch", AppConfigProvider.appGitBranch))
                systemInfoManager.getSigningCertFingerprint()?.let {
                    add(BlockContent.TitleValue("App Signature", it))
                }
                add(BlockContent.TitleValue("Device Model", systemInfoManager.deviceModel))
                add(BlockContent.TitleValue("OS Version", systemInfoManager.osVersion))
                addAll(getDeviceClass(context))
                add(
                    BlockContent.TitleValue(
                        "System pin required",
                        if (localStorage.isSystemPinRequired) "Yes" else "No"
                    )
                )
            }
        )
    }

    private fun getEnabledBlockchainsString(): String {
        val names = walletManager.activeWallets
            .map { it.token.blockchainType }
            .distinct()
            .mapNotNull { marketKit.blockchain(it.uid)?.name }
            .sorted()

        return if (names.isEmpty()) "None" else names.joinToString(", ")
    }

    private fun getEnabledBlockchainsBlock(): AppStatusModule.BlockData {
        return AppStatusModule.BlockData(
            title = "Enabled Blockchains",
            content = listOf(
                BlockContent.TitleValue("Blockchains", getEnabledBlockchainsString()),
                BlockContent.TitleValue(
                    "Premium Status",
                    checkPremiumUseCase.getPremiumType().name
                ),
            )
        )
    }

    private fun getEnabledBlockchains(): Map<String, Any> {
        return linkedMapOf(
            "Blockchains" to getEnabledBlockchainsString(),
            "Premium Status" to checkPremiumUseCase.getPremiumType().name
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
            add(BlockContent.TitleValue("App Heap Limit", "${memory}MB (${largeMemory}MB)"))
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

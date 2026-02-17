# MOBILE-512: Blockchain Status Screen — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a dedicated Blockchain Status screen for BTC (generic for future blockchains) with copy/share, kit version, filtered logs, and deduped peers.

**Architecture:** Generic `BlockchainStatusProvider` interface in `modules/blockchainstatus/`, BTC implementation using existing `BtcBlockchainSettingsService`. Internal NavHost in `BtcBlockchainSettingsFragment` routes between settings and status screens.

**Tech Stack:** Kotlin, Jetpack Compose, Room (LogsDao), Koin DI, Navigation Compose

**Design doc:** `docs/plans/2026-02-17-blockchain-status-design.md`

---

### Task 1: Add log filtering to LogsDao and AppLog

**Files:**
- Modify: `core/core/src/main/java/io/horizontalsystems/core/storage/LogsDao.kt:9-20`
- Modify: `core/core/src/main/java/io/horizontalsystems/core/logger/AppLog.kt:14-75`

**Step 1: Add `getByTag` query to LogsDao**

In `core/core/src/main/java/io/horizontalsystems/core/storage/LogsDao.kt`, add after `getAll()` (line 15):

```kotlin
@Query("SELECT * FROM LogEntry WHERE actionId LIKE '%' || :tag || '%' ORDER BY id")
fun getByTag(tag: String): List<LogEntry>
```

**Step 2: Add `getLog(tag)` method to AppLog**

In `core/core/src/main/java/io/horizontalsystems/core/logger/AppLog.kt`, add after `getLog()` (line 53):

```kotlin
fun getLog(tag: String): Map<String, Any> {
    val res = mutableMapOf<String, MutableMap<String, String>>()

    logsDao.getByTag(tag).forEach { logEntry ->
        if (!res.containsKey(logEntry.actionId)) {
            res[logEntry.actionId] = mutableMapOf()
        }

        val logMessage = sdf.format(Date(logEntry.date)) + " " + logEntry.message

        res[logEntry.actionId]?.set(logEntry.id.toString(), logMessage)
    }

    return res
}
```

**Step 3: Verify compilation**

Run: `./gradlew :core:core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add core/core/src/main/java/io/horizontalsystems/core/storage/LogsDao.kt core/core/src/main/java/io/horizontalsystems/core/logger/AppLog.kt
git commit -m "Add filtered log query by actionId tag"
```

---

### Task 2: Add BuildConfig field for Bitcoin kit version

**Files:**
- Modify: `app/build.gradle:57`

**Step 1: Add buildConfigField**

In `app/build.gradle`, after line 57 (`buildConfigField "String", "GIT_HASH", ...`), add:

```groovy
buildConfigField "String", "BITCOIN_KIT_VERSION", "\"${libs.versions.piratecash.bitcoin.get()}\""
```

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `BuildConfig.BITCOIN_KIT_VERSION` now available as `"d62eff5"`.

**Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "Add BITCOIN_KIT_VERSION to BuildConfig from libs.versions.toml"
```

---

### Task 3: Create BlockchainStatusProvider interface and BTC implementation

**Files:**
- Create: `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusProvider.kt`

**Step 1: Create the provider file**

Create `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusProvider.kt`:

```kotlin
package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

data class StatusSection(
    val title: String,
    val items: List<StatusItem>
)

sealed class StatusItem {
    data class KeyValue(val key: String, val value: String) : StatusItem()
    data class Nested(val title: String, val items: List<KeyValue>) : StatusItem()
}

interface BlockchainStatusProvider {
    val blockchainName: String
    val kitVersion: String
    val logFilterTag: String

    fun getStatusSections(): List<StatusSection>
    fun getSharedSection(): StatusSection?
}

class BtcBlockchainStatusProvider(
    private val blockchain: Blockchain,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) : BlockchainStatusProvider {

    override val blockchainName: String = blockchain.name

    override val kitVersion: String = BuildConfig.BITCOIN_KIT_VERSION

    override val logFilterTag: String = blockchain.type.logTag

    override fun getStatusSections(): List<StatusSection> {
        return getWalletStatusPairs().map { (label, statusMap) ->
            val items = statusMap
                .filter { (_, value) -> value !is Map<*, *> }
                .map { (key, value) -> StatusItem.KeyValue(key, value.toString()) }

            StatusSection(title = label, items = items)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getSharedSection(): StatusSection? {
        val firstStatusMap = getWalletStatusPairs().firstOrNull()?.second ?: return null

        val peerItems = firstStatusMap
            .filter { (_, value) -> value is Map<*, *> }
            .map { (key, value) ->
                val peerMap = value as Map<String, Any>
                StatusItem.Nested(
                    title = key,
                    items = peerMap.map { (k, v) -> StatusItem.KeyValue(k, v.toString()) }
                )
            }

        if (peerItems.isEmpty()) return null

        return StatusSection(
            title = "${blockchain.name} Peers",
            items = peerItems
        )
    }

    private fun getWalletStatusPairs(): List<Pair<String, Map<String, Any>>> {
        return walletManager.activeWallets
            .filter { it.token.blockchainType == blockchain.type }
            .mapNotNull { wallet ->
                val adapter = adapterManager.getAdapterForWallet<BitcoinBaseAdapter>(wallet)
                    ?: return@mapNotNull null
                val label = wallet.badge ?: wallet.token.coin.name
                val restoreMode = btcBlockchainManager.restoreMode(wallet.token.blockchainType)
                val statusInfo = mutableMapOf<String, Any>("Sync Mode" to restoreMode.name)
                statusInfo.putAll(adapter.statusInfo)
                label to statusInfo
            }
    }
}

val BlockchainType.logTag: String
    get() = when (this) {
        BlockchainType.Bitcoin -> "BTC"
        BlockchainType.Litecoin -> "LTC"
        BlockchainType.BitcoinCash -> "BCH"
        BlockchainType.Dash -> "DASH"
        BlockchainType.ECash -> "XEC"
        BlockchainType.Dogecoin -> "DOGE"
        else -> name
    }
```

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusProvider.kt
git commit -m "Add BlockchainStatusProvider interface and BTC implementation"
```

---

### Task 4: Create BlockchainStatusViewModel

**Files:**
- Create: `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusViewModel.kt`

**Step 1: Create the ViewModel**

Create `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusViewModel.kt`:

```kotlin
package cash.p.terminal.modules.blockchainstatus

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.BuildConfig
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class BlockchainStatusViewModel(
    private val provider: BlockchainStatusProvider,
    private val context: Context
) : ViewModel() {

    private var shareFile: File? = null

    private val _uiState = MutableStateFlow(
        BlockchainStatusUiState(
            blockchainName = provider.blockchainName,
            kitVersion = provider.kitVersion,
            statusSections = emptyList(),
            sharedSection = null,
            logBlocks = emptyList(),
            statusAsText = null,
            loading = true
        )
    )
    val uiState: StateFlow<BlockchainStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val statusSections = provider.getStatusSections()
            val sharedSection = provider.getSharedSection()
            val appLogs = AppLog.getLog(provider.logFilterTag)
            val logBlocks = buildLogBlocks(appLogs)
            val statusAsText = buildStatusText(statusSections, sharedSection, appLogs)

            statusAsText?.let { text ->
                try {
                    val file = File(context.cacheDir, "blockchain_status_report.txt")
                    file.writeText(text)
                    shareFile = file
                } catch (_: Exception) {
                    // File write failed, share will be unavailable
                }
            }

            _uiState.value = BlockchainStatusUiState(
                blockchainName = provider.blockchainName,
                kitVersion = provider.kitVersion,
                statusSections = statusSections,
                sharedSection = sharedSection,
                logBlocks = logBlocks,
                statusAsText = statusAsText,
                loading = false
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

    private fun buildLogBlocks(appLogs: Map<String, Any>): List<LogBlock> {
        return appLogs.map { (key, value) ->
            val content = formatMapValue(value)
            LogBlock(title = key.replaceFirstChar(Char::uppercase), content = content)
        }
    }

    private fun buildStatusText(
        sections: List<StatusSection>,
        sharedSection: StatusSection?,
        appLogs: Map<String, Any>
    ): String = buildString {
        appendLine("${provider.blockchainName} Status")
        appendLine("Kit Version: ${provider.kitVersion}")
        appendLine()

        sections.forEach { section ->
            appendLine(section.title)
            section.items.forEach { item ->
                when (item) {
                    is StatusItem.KeyValue -> appendLine("  ${item.key}: ${item.value}")
                    is StatusItem.Nested -> {
                        appendLine("  ${item.title}:")
                        item.items.forEach { kv ->
                            appendLine("    ${kv.key}: ${kv.value}")
                        }
                    }
                }
            }
            appendLine()
        }

        sharedSection?.let { section ->
            appendLine(section.title)
            section.items.forEach { item ->
                when (item) {
                    is StatusItem.KeyValue -> appendLine("  ${item.key}: ${item.value}")
                    is StatusItem.Nested -> {
                        appendLine("  ${item.title}:")
                        item.items.forEach { kv ->
                            appendLine("    ${kv.key}: ${kv.value}")
                        }
                    }
                }
            }
            appendLine()
        }

        if (appLogs.isNotEmpty()) {
            appendLine("App Log")
            appLogs.forEach { (key, value) ->
                appendLine(key)
                appendLine(formatMapValue(value))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun formatMapValue(value: Any, indent: String = "  "): String = buildString {
        when (value) {
            is Map<*, *> -> {
                (value as Map<String, Any>).forEach { (k, v) ->
                    when (v) {
                        is Map<*, *> -> {
                            appendLine("$indent$k:")
                            append(formatMapValue(v, "$indent  "))
                        }
                        is Date -> {
                            val date = DateHelper.formatDate(v, "MMM d, yyyy, HH:mm")
                            appendLine("$indent$k: $date")
                        }
                        else -> appendLine("$indent$k: $v")
                    }
                }
            }
            else -> appendLine("$indent$value")
        }
    }
}

data class LogBlock(
    val title: String,
    val content: String
)

@Immutable
data class BlockchainStatusUiState(
    val blockchainName: String,
    val kitVersion: String,
    val statusSections: List<StatusSection>,
    val sharedSection: StatusSection?,
    val logBlocks: List<LogBlock>,
    val statusAsText: String?,
    val loading: Boolean
)
```

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusViewModel.kt
git commit -m "Add BlockchainStatusViewModel with copy/share and filtered logs"
```

---

### Task 5: Create BlockchainStatusScreen composable

**Files:**
- Create: `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusScreen.kt`

**Reference:** Copy/share button layout from `app/src/main/java/cash/p/terminal/modules/settings/appstatus/AppStatusScreen.kt:86-129`. Status section rendering reuses `CellUniversalLawrenceSection` + `RowUniversal` pattern from existing `BlockchainStatusSection` in `BtcBlockchainSettingsScreen.kt:156-174`.

**Step 1: Create the screen**

Create `app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusScreen.kt`:

```kotlin
package cash.p.terminal.modules.blockchainstatus

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun BlockchainStatusScreen(
    viewModel: BlockchainStatusViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val localView = LocalView.current
    val context = LocalContext.current

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.blockchain_status),
                navigationIcon = {
                    HsBackButton(onClick = onBack)
                },
            )
        }
    ) { paddingValues ->
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = ComposeAppTheme.colors.grey,
                    strokeWidth = 4.dp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    ) {
                        ButtonPrimaryYellow(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.Button_Copy),
                            onClick = {
                                uiState.statusAsText?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    HudHelper.showSuccessMessage(
                                        localView,
                                        R.string.Hud_Text_Copied
                                    )
                                }
                            }
                        )
                        HSpacer(8.dp)
                        ButtonPrimaryDefault(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.Button_Share),
                            onClick = {
                                try {
                                    val uri = viewModel.getShareFileUri(context)
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                context.getString(R.string.blockchain_status)
                                            )
                                            addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    or Intent.FLAG_ACTIVITY_NEW_TASK
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(intent, null))
                                    } else {
                                        HudHelper.showErrorMessage(
                                            localView,
                                            R.string.error_cannot_create_log_file
                                        )
                                    }
                                } catch (e: Exception) {
                                    HudHelper.showErrorMessage(
                                        localView,
                                        e.message ?: context.getString(R.string.Error)
                                    )
                                }
                            }
                        )
                    }
                }

                // Kit Version
                item {
                    KitVersionBlock(uiState.kitVersion)
                }

                // Per-address-type status sections
                items(uiState.statusSections) { section ->
                    StatusSectionBlock(section)
                }

                // Shared peers section
                uiState.sharedSection?.let { section ->
                    item {
                        StatusSectionBlock(section)
                    }
                }

                // Filtered app logs
                if (uiState.logBlocks.isNotEmpty()) {
                    item {
                        InfoText(text = "APP LOG")
                    }
                    items(uiState.logBlocks) { logBlock ->
                        CellUniversalLawrenceSection(
                            listOf(logBlock)
                        ) { block ->
                            RowUniversal(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    subhead2_leah(text = block.title)
                                    subhead2_grey(text = block.content.trimEnd())
                                }
                            }
                        }
                        VSpacer(12.dp)
                    }
                }

                item {
                    VSpacer(32.dp)
                }
            }
        }
    }
}

@Composable
private fun KitVersionBlock(kitVersion: String) {
    VSpacer(12.dp)
    InfoText(text = "KIT VERSION")
    CellUniversalLawrenceSection(
        listOf(kitVersion)
    ) { version ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            subhead2_grey(
                modifier = Modifier.weight(1f),
                text = "Version"
            )
            subhead1_leah(text = version)
        }
    }
}

@Composable
private fun StatusSectionBlock(section: StatusSection) {
    VSpacer(12.dp)
    InfoText(text = section.title.uppercase())
    val displayItems = section.items
    CellUniversalLawrenceSection(displayItems) { item ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            when (item) {
                is StatusItem.KeyValue -> {
                    subhead2_grey(
                        modifier = Modifier.weight(1f),
                        text = item.key
                    )
                    subhead1_leah(
                        modifier = Modifier.padding(start = 8.dp),
                        text = item.value
                    )
                }
                is StatusItem.Nested -> {
                    Column(modifier = Modifier.weight(1f)) {
                        body_leah(text = item.title)
                        item.items.forEach { kv ->
                            Row {
                                subhead2_grey(text = "  ${kv.key}: ")
                                subhead2_grey(text = kv.value)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/cash/p/terminal/modules/blockchainstatus/BlockchainStatusScreen.kt
git commit -m "Add BlockchainStatusScreen composable with copy/share UI"
```

---

### Task 6: Register ViewModel in Koin

**Files:**
- Modify: `app/src/main/java/cash/p/terminal/di/ViewModelModule.kt:69`

**Step 1: Add import and registration**

Add import at top of file:
```kotlin
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
```

After line 69 (`viewModelOf(::AppStatusViewModel)`), add:
```kotlin
viewModel { params -> BlockchainStatusViewModel(provider = params.get(), context = get()) }
```

Note: We use `viewModel { params -> }` (not `viewModelOf`) because the provider is a runtime parameter passed from the composable, not a Koin-managed dependency.

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/cash/p/terminal/di/ViewModelModule.kt
git commit -m "Register BlockchainStatusViewModel in Koin with runtime provider param"
```

---

### Task 7: Wire up navigation — modify BtcBlockchainSettingsFragment

**Files:**
- Modify: `app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsFragment.kt`

**Reference:** NavHost pattern from `app/src/main/java/cash/p/terminal/modules/settings/about/AboutFragment.kt:64-103`.

**Step 1: Rewrite Fragment with internal NavHost**

Replace the entire content of `BtcBlockchainSettingsFragment.kt` with:

```kotlin
package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.core.App
import cash.p.terminal.core.composablePage
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
import cash.p.terminal.modules.blockchainstatus.BtcBlockchainStatusProvider
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.requireInput
import io.horizontalsystems.core.entities.Blockchain
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SettingsPage = "settings"
private const val StatusPage = "status"

class BtcBlockchainSettingsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val blockchain = navController.requireInput<Blockchain>()
        BtcBlockchainSettingsNavHost(blockchain, navController)
    }
}

@Composable
private fun BtcBlockchainSettingsNavHost(
    blockchain: Blockchain,
    fragmentNavController: NavController
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SettingsPage,
    ) {
        composable(SettingsPage) {
            val viewModel = viewModel<BtcBlockchainSettingsViewModel>(
                factory = BtcBlockchainSettingsModule.Factory(blockchain)
            )
            BtcBlockchainSettingsScreen(
                uiState = viewModel.uiState,
                fragmentNavController = fragmentNavController,
                onSaveClick = viewModel::onSaveClick,
                onSelectRestoreMode = viewModel::onSelectRestoreMode,
                onCustomPeersChange = viewModel::onCustomPeersChange,
                onBlockchainStatusClick = { navController.navigate(StatusPage) }
            )
        }
        composablePage(StatusPage) {
            val provider = remember {
                BtcBlockchainStatusProvider(
                    blockchain = blockchain,
                    btcBlockchainManager = App.btcBlockchainManager,
                    walletManager = App.walletManager,
                    adapterManager = App.adapterManager
                )
            }
            val viewModel = koinViewModel<BlockchainStatusViewModel> {
                parametersOf(provider)
            }
            cash.p.terminal.modules.blockchainstatus.BlockchainStatusScreen(
                viewModel = viewModel,
                onBack = navController::popBackStack
            )
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: May fail because `BtcBlockchainSettingsScreen` signature changed (needs `onBlockchainStatusClick` and `fragmentNavController`). That's Task 8.

**Step 3: Commit** (after Task 8 compiles together)

---

### Task 8: Modify BtcBlockchainSettingsScreen — replace inline status with button

**Files:**
- Modify: `app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsScreen.kt`

**Step 1: Update screen signature and body**

Replace the `BtcBlockchainSettingsScreen` composable (lines 49-129) — change `navController` to `fragmentNavController`, add `onBlockchainStatusClick`, replace `BlockchainStatusSection` with a clickable button:

```kotlin
@Composable
internal fun BtcBlockchainSettingsScreen(
    uiState: BtcBlockchainSettingsUIState,
    onSaveClick: () -> Unit,
    onSelectRestoreMode: (BtcBlockchainSettingsModule.ViewItem) -> Unit,
    onCustomPeersChange: (String) -> Unit,
    fragmentNavController: NavController,
    onBlockchainStatusClick: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {

    if (uiState.closeScreen) {
        fragmentNavController.popBackStack()
    }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column(modifier = Modifier.windowInsetsPadding(windowInsets)) {
            AppBar(
                title = uiState.title,
                navigationIcon = {
                    Image(
                        painter = rememberAsyncImagePainterWithFallback(
                            model = uiState.blockchainIconUrl,
                            error = painterResource(R.drawable.ic_platform_placeholder_32)
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .size(24.dp)
                    )
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Close),
                        icon = R.drawable.ic_close,
                        onClick = {
                            fragmentNavController.popBackStack()
                        }
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                RestoreSourceSettings(uiState.restoreSources, onSelectRestoreMode)
                if (uiState.customPeers != null) {
                    Spacer(Modifier.height(16.dp))
                    CustomPeersSettings(
                        customPeers = uiState.customPeers,
                        onCustomPeersChange = onCustomPeersChange
                    )
                }
                VSpacer(32.dp)
                BlockchainStatusButton(onBlockchainStatusClick)
                Spacer(Modifier.height(32.dp))
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.BtcBlockchainSettings_RestoreSourceChangeWarning)
                )
                Spacer(Modifier.height(32.dp))
            }

            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    title = stringResource(R.string.Button_Save),
                    enabled = uiState.saveButtonEnabled,
                    onClick = onSaveClick
                )
            }
        }

    }
}
```

**Step 2: Add the BlockchainStatusButton composable**

Replace the old `BlockchainStatusSection` composable (lines 155-174) with:

```kotlin
@Composable
private fun BlockchainStatusButton(onClick: () -> Unit) {
    CellUniversalLawrenceSection(listOf(Unit)) {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = onClick
        ) {
            body_leah(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.blockchain_status)
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                tint = ComposeAppTheme.colors.grey,
                contentDescription = null,
            )
        }
    }
}
```

**Step 3: Remove unused imports**

Remove the `StatusBlockItem` import (line 32):
```kotlin
// Remove this line:
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.StatusBlockItem
```

Add `Icon` import if not present:
```kotlin
import androidx.compose.material.Icon
```

**Step 4: Update preview**

Update `BtcBlockchainSettingsScreenPreview` (lines 255-290) — remove `statusItems`, change `navController` to `fragmentNavController`, add `onBlockchainStatusClick`:

```kotlin
@Composable
@Preview(showBackground = true)
private fun BtcBlockchainSettingsScreenPreview() {
    ComposeAppTheme {
        BtcBlockchainSettingsScreen(
            uiState = BtcBlockchainSettingsUIState(
                title = "Bitcoin",
                blockchainIconUrl = "https://bitcoin.org/favicon.png",
                restoreSources = listOf(
                    BtcBlockchainSettingsModule.ViewItem(
                        id = "1",
                        title = "Blockchair",
                        subtitle = "Blockchair is a blockchain search and analytics engine",
                        selected = true,
                        icon = BlockchainSettingsIcon.ApiIcon(R.drawable.ic_blockchair)
                    ),
                    BtcBlockchainSettingsModule.ViewItem(
                        id = "2",
                        title = "Hybrid",
                        subtitle = "Hybrid is a blockchain search and analytics engine",
                        selected = false,
                        icon = BlockchainSettingsIcon.ApiIcon(R.drawable.ic_api_hybrid)
                    )
                ),
                saveButtonEnabled = true,
                closeScreen = false,
                customPeers = ""
            ),
            onSaveClick = {},
            onSelectRestoreMode = {},
            onCustomPeersChange = {},
            fragmentNavController = rememberNavController(),
            onBlockchainStatusClick = {}
        )
    }
}
```

**Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit Tasks 7 + 8 together**

```bash
git add app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsFragment.kt app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsScreen.kt
git commit -m "Wire navigation: NavHost in BtcBlockchainSettings, replace inline status with button"
```

---

### Task 9: Clean up BtcBlockchainSettingsViewModel — remove statusItems

**Files:**
- Modify: `app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsViewModel.kt:31,40,56-58,100-114`
- Modify: `app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsModule.kt:39-42`

**Step 1: Remove statusItems from ViewModel**

In `BtcBlockchainSettingsViewModel.kt`:

1. Remove line 9 (StatusBlockItem import):
   ```kotlin
   // Delete: import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.StatusBlockItem
   ```

2. Remove line 31:
   ```kotlin
   // Delete: private var statusItems = emptyList<StatusBlockItem>()
   ```

3. Remove `statusItems` from `createState()` (line 40):
   ```kotlin
   // Remove: statusItems = statusItems
   ```

4. Remove lines 56-58 (statusItems initialization in `init`):
   ```kotlin
   // Delete these lines:
   // statusItems = service.getStatusInfo().map { (label, statusMap) ->
   //     StatusBlockItem(label, formatStatusMap(statusMap))
   // }
   ```

5. Remove `formatStatusMap` method entirely (lines 100-114):
   ```kotlin
   // Delete the entire formatStatusMap function
   ```

**Step 2: Remove statusItems from UIState**

In `BtcBlockchainSettingsUIState` (line 146), remove the `statusItems` field:
```kotlin
// Remove: val statusItems: List<StatusBlockItem> = emptyList()
```

**Step 3: Remove StatusBlockItem from Module**

In `BtcBlockchainSettingsModule.kt`, remove lines 39-42:
```kotlin
// Delete:
// data class StatusBlockItem(
//     val label: String,
//     val statusText: String
// )
```

**Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsViewModel.kt app/src/main/java/cash/p/terminal/modules/btcblockchainsettings/BtcBlockchainSettingsModule.kt
git commit -m "Remove inline statusItems from BtcBlockchainSettings (moved to BlockchainStatus)"
```

---

### Task 10: Add new string resources to all 14 locales

**Files:**
- Modify: `core/strings/src/main/res/values/strings.xml` (and 13 other locale files)

**Context:** The string `blockchain_status` (= "Blockchain Status") already exists in all locales. We need to add `blockchain_status_kit_version` and `blockchain_status_peers`.

Wait — looking at the screen design, we use `InfoText` for section headers which just takes plain strings, and the `blockchain_status` string already exists. The kit version section title is hardcoded as "KIT VERSION" and the peers section title comes from the provider (`"Bitcoin Peers"`). No new string resources are actually needed beyond what exists.

**Step 1: Verify no new strings are needed**

Check the screen composable: all text comes from either existing strings (`blockchain_status`, `Button_Copy`, `Button_Share`, `Hud_Text_Copied`, `error_cannot_create_log_file`, `Error`) or from the provider data directly. No new strings needed.

**Step 2: Skip — no commit needed**

---

### Task 11: Final verification — compile and manual smoke test

**Step 1: Full compilation check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 2: Check for unused imports**

Scan modified files for unused imports. The removed `StatusBlockItem` may have left orphaned imports.

**Step 3: Verify the complete flow works**

Manual test checklist:
1. Open Settings -> Blockchain Settings -> Bitcoin
2. Verify "Blockchain Status" button appears (with arrow icon)
3. Verify inline status block is gone
4. Tap "Blockchain Status" — new screen opens with slide animation
5. Verify: Kit Version block shows `d62eff5`
6. Verify: Per-address-type status sections (BIP44, BIP49, BIP84) show sync info
7. Verify: Single "Bitcoin Peers" section at bottom (not duplicated per address type)
8. Verify: Copy button copies all status text to clipboard
9. Verify: Share button opens share sheet with text file
10. Verify: App Log section shows only BTC-related logs
11. Verify: Back button returns to settings screen
12. Verify: Save button on settings screen still works

**Step 4: Final commit if any fixes needed**

```bash
git add -A
git commit -m "Final cleanup for MOBILE-512 blockchain status screen"
```

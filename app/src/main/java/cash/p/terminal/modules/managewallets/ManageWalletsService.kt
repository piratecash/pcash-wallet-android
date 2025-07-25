package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.eligibleTokens
import cash.p.terminal.core.isDefault
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.order
import cash.p.terminal.core.restoreSettingTypes
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asFlow
import org.koin.java.KoinJavaComponent.inject

class ManageWalletsService(
    private val walletManager: IWalletManager,
    private val restoreSettingsService: RestoreSettingsService,
    private val fullCoinsProvider: FullCoinsProvider?,
    private val account: Account?
) : Clearable {

    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase by inject(
        GetHardwarePublicKeyForWalletUseCase::class.java
    )

    private val _itemsFlow = MutableStateFlow<List<Item>>(listOf())
    val itemsFlow
        get() = _itemsFlow.asStateFlow()

    val accountType: AccountType?
        get() = account?.type

    private var fullCoins = listOf<FullCoin>()
    private var items = listOf<Item>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var filter: String = ""

    init {
        coroutineScope.launch {
            walletManager.activeWalletsUpdatedObservable.asFlow().collect {
                handleUpdated(it)
            }
        }
        coroutineScope.launch {
            restoreSettingsService.approveSettingsObservable.asFlow().collect {
                enable(it.token, it.settings)
            }
        }

        sync(walletManager.activeWallets)
        syncFullCoins()
        sortItems()
        syncState()
    }

    private fun isEnabled(token: Token): Boolean {
        return walletManager.activeWallets.any { it.token == token }
    }

    private fun sync(walletList: List<Wallet>) {
        fullCoinsProvider?.setActiveWallets(walletList)
    }

    private fun fetchFullCoins(): List<FullCoin> {
        return fullCoinsProvider?.getItems() ?: listOf()
    }

    private fun syncFullCoins() {
        fullCoins = fetchFullCoins()
    }

    private fun sortItems() {
        var comparator = compareByDescending<Item> {
            it.enabled
        }

        if (filter.isBlank()) {
            comparator = comparator.thenBy {
                it.token.blockchain.type.order
            }
        }

        items = fullCoins
            .map { getItemsForFullCoin(it) }
            .flatten()
            .sortedWith(comparator)
    }

    private fun getItemsForFullCoin(fullCoin: FullCoin): List<Item> {
        val accountType = account?.type ?: return listOf()
        val eligibleTokens = fullCoin.eligibleTokens(accountType)

        val tokens = if (filter.isNotBlank()) {
            eligibleTokens
        } else if (
            accountType !is AccountType.HdExtendedKey &&
            (eligibleTokens.all { it.type is TokenType.Derived } ||
                    eligibleTokens.all { it.type is TokenType.AddressTyped } ||
                    eligibleTokens.all { it.type is TokenType.AddressSpecTyped })
        ) {
            eligibleTokens.filter { isEnabled(it) || it.type.isDefault }
        } else {
            eligibleTokens.filter { isEnabled(it) || it.type.isNative }
        }

        return tokens.map { getItemForToken(it) }
    }

    private fun getItemForToken(token: Token): Item {
        val enabled = isEnabled(token)

        return Item(
            token = token,
            enabled = enabled,
            hasInfo = hasInfo(token, enabled)
        )
    }

    private fun hasInfo(token: Token, enabled: Boolean) = when (token.type) {
        is TokenType.Native -> token.blockchainType is BlockchainType.Zcash && enabled
        is TokenType.Derived,
        is TokenType.AddressTyped,
        is TokenType.Eip20,
        is TokenType.Spl,
        is TokenType.Jetton -> true

        else -> false
    }

    private fun syncState() {
        _itemsFlow.update {
            buildList { addAll(items) }
        }
    }

    private fun handleUpdated(wallets: List<Wallet>) {
        sync(wallets)

        val newFullCons = fetchFullCoins()
        if (newFullCons.size > fullCoins.size) {
            fullCoins = newFullCons
            sortItems()
        }

        syncState()
    }

    private fun updateSortedItems(token: Token, enable: Boolean) {
        items = items.map { item ->
            if (item.token == token) {
                item.copy(
                    enabled = enable,
                    hasInfo = hasInfo(token, enable)
                )
            } else {
                item
            }
        }
    }

    private fun enable(token: Token, restoreSettings: RestoreSettings) {
        val account = this.account ?: return

        if (restoreSettings.isNotEmpty()) {
            restoreSettingsService.save(restoreSettings, account, token.blockchainType)
        }
        val hardwarePublicKey = runBlocking {
            getHardwarePublicKeyForWalletUseCase(account, token)
        }

        walletManager.save(listOf(Wallet(token, account, hardwarePublicKey)))

        updateSortedItems(token, true)
    }

    fun setFilter(filter: String) {
        this.filter = filter
        fullCoinsProvider?.setQuery(filter)

        syncFullCoins()
        sortItems()
        syncState()
    }

    fun enable(token: Token) {
        val account = this.account ?: return

        if (token.blockchainType.restoreSettingTypes.isNotEmpty()) {
            restoreSettingsService.approveSettings(token, account)
        } else {
            enable(token, RestoreSettings())
        }
    }

    fun disable(token: Token) {
        walletManager.activeWallets
            .firstOrNull { it.token == token }
            ?.let {
                walletManager.delete(listOf(it))
                updateSortedItems(token, false)
            }
    }

    override fun clear() {
        coroutineScope.cancel()
    }

    data class Item(
        val token: Token,
        val enabled: Boolean,
        val hasInfo: Boolean
    )
}

package cash.p.terminal.premium.domain.usecase

import android.os.SystemClock
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.data.TokenBalance
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.model.AccountPremiumCacheEntity
import cash.p.terminal.premium.data.model.PremiumUser
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.eligibleForPremium
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal class CheckPremiumUseCaseImpl(
    private val premiumUserRepository: PremiumUserRepository,
    private val demoPremiumUserDao: DemoPremiumUserDao,
    private val binanceApi: BinanceApi,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val checkAdapterPremiumBalanceUseCase: CheckAdapterPremiumBalanceUseCase,
    private val checkTrialPremiumUseCase: CheckTrialPremiumUseCase,
    private val activateTrialPremiumUseCase: ActivateTrialPremiumUseCase,
    private val getBnbAddressUseCase: GetBnbAddressUseCase,
    private val userManager: UserManager,
    private val accountPremiumCacheDao: AccountPremiumCacheDao,
    private val dispatcherProvider: DispatcherProvider
) : CheckPremiumUseCase {

    private val mutex = Mutex()
    // Bounds global concurrency of live balance checks across the background warm-up and any screen scan;
    // the wait for a permit is itself covered by the per-account timeout so the queue cannot grow unbounded.
    private val premiumCheckSemaphore = Semaphore(PREMIUM_CHECK_CONCURRENCY)

    private val _premiumCache = MutableStateFlow<Map<Int, PremiumType>>(emptyMap())
    private val _trialPremiumCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _levelAccountCache = MutableStateFlow<Map<Int, String>>(emptyMap())
    // Per-account TOKEN premium cache. Hydrated from Room on startup (entries with checkedAtNanos = null:
    // displayable but not fresh, so the warm-up force-rescans them) and refreshed by the background warm-up,
    // which is the ONLY writer. Trial is layered on top only in [premiumTypesFlow].
    private val _accountPremiumCache = MutableStateFlow<Map<String, CachedPremiumType>>(emptyMap())
    private val scope = CoroutineScope(dispatcherProvider.default + SupervisorJob())

    private val coinConfigs = mapOf(
        PremiumConfig.COIN_TYPE_PIRATE to CoinConfig(
            contractAddress = PremiumConfig.PIRATE_CONTRACT_ADDRESS,
            minAmount = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE
        ),
        PremiumConfig.COIN_TYPE_COSANTA to CoinConfig(
            contractAddress = PremiumConfig.COSANTA_CONTRACT_ADDRESS,
            minAmount = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA
        )
    )

    // Display view of the per-account premium type (any age): cached token/NONE with an active trial
    // layered on top. Consumed by the wallet-list screens so a cold start shows the hydrated value
    // immediately and updates as the background re-scan completes.
    override val premiumTypesFlow: StateFlow<Map<String, PremiumType>> =
        combine(_accountPremiumCache, _trialPremiumCache) { accountCache, trialCache ->
            buildMap {
                accountCache.forEach { (id, cached) -> put(id, cached.type) }
                trialCache.forEach { (id, active) -> if (active) put(id, PremiumType.TRIAL) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            // Hydrate first so premiumTypesFlow can show the last known type before the re-scan runs.
            hydrateAccountPremiumCache()
            // Subscribe before snapshotting the account set so a replay-0 accountsFlow emission during app
            // startup (accounts loaded right after eager construction) is not missed and the warm-up still
            // runs. A single pipeline drives both the initial warm-up and every later account/level change.
            val accountsWithInitial = accountManager.accountsFlow.let { flow ->
                if (flow is SharedFlow) {
                    flow.onSubscription { emit(accountManager.accounts) }
                } else {
                    flow.onStart { emit(accountManager.accounts) }
                }
            }
            combine(userManager.currentUserLevelFlow, accountsWithInitial) { _, _ -> Unit }
                .collectLatest {
                    // Prune runs first, inside the same pipeline: collectLatest already cancelled the
                    // previous iteration, so a removed account is dropped before the new scan and cannot
                    // race a stale upsert. Initial emission does startup reconciliation.
                    pruneDeletedAccounts()
                    update()
                    warmAccountPremiumCache()
                }
        }
    }

    /**
     * Seeds [_accountPremiumCache] from the persisted cache. Hydrated entries carry checkedAtNanos = null:
     * they are displayed at any age but treated as not-fresh, so the warm-up force-rescans them this launch.
     * Isolated so a read failure cannot kill the init pipeline.
     */
    private suspend fun hydrateAccountPremiumCache() {
        try {
            _accountPremiumCache.value = accountPremiumCacheDao.getAll().associate {
                it.accountId to CachedPremiumType(it.premiumType, checkedAtNanos = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to hydrate account premium cache")
        }
    }

    /**
     * Removes cache entries (memory + disk) for accounts that were actually deleted. Uses the authoritative
     * deleted-id list, NOT "not in the current account set" — duress switching legitimately hides accounts
     * for a level, and those must not be pruned. Isolated so a Room failure cannot kill the init pipeline.
     */
    private suspend fun pruneDeletedAccounts() {
        try {
            val deleted = withContext(dispatcherProvider.io) { accountManager.getDeletedAccountIds() }
            if (deleted.isEmpty()) return
            accountPremiumCacheDao.deleteByAccountIds(deleted)
            _accountPremiumCache.update { it - deleted }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to prune deleted accounts from premium cache")
        }
    }

    /**
     * Refreshes the per-account TOKEN premium cache in the background (on startup and whenever the account
     * set / user level changes), the ONLY writer of that cache, so the wallet-list screens observe a warm
     * cache instead of running live balance checks on demand.
     */
    private suspend fun warmAccountPremiumCache() {
        getPremiumTypesForAccounts(accountManager.accounts)
    }

    override fun getPremiumType(): PremiumType {
        return getPremiumTypeForLevel(userManager.currentUserLevelFlow.value)
    }

    override fun getParentPremiumType(userLevel: Int): PremiumType {
        return getPremiumTypeForLevel(getParentLevel(userLevel))
    }

    override suspend fun isPremiumWithParentInCache(userLevel: Int): Boolean {
            // Check token premium (PIRATE/COSA) by level
            val hasTokenPremium = premiumUserRepository.getByLevels(
                listOf(
                    userLevel,
                    getParentLevel(userLevel)
                )
            ).any { it.isPremium.isPremium() }

            if (hasTokenPremium) return true

            // Check trial premium (any active trial in cache)
            return withContext(dispatcherProvider.io) { demoPremiumUserDao.hasActiveTrialPremium() }
        }

    private fun getParentLevel(userLevel: Int): Int {
        return if (userLevel > 0) userLevel - 1 else userLevel
    }

    private fun getPremiumTypeForLevel(level: Int): PremiumType {
        val currentLevel = userManager.currentUserLevelFlow.value

        // For current level, use active account; for other levels, use cached accountId
        val accountId = if (level == currentLevel) {
            accountManager.activeAccount?.id
        } else {
            _levelAccountCache.value[level]
        }

        // Check trial premium if we have an accountId
        if (accountId != null && _trialPremiumCache.value[accountId] == true) {
            return PremiumType.TRIAL
        }

        val premiumType = _premiumCache.value[level]
        if (premiumType?.isPremium() != true && accountId != null) {
            val adapterResult = checkAdapterPremiumBalanceUseCase()
            if (adapterResult is CheckAdapterPremiumBalanceUseCase.Result.Premium) {
                scope.launch {
                    // to keep cache updated
                    updateAdapterBalance()
                }
                return adapterResult.premiumType
            }
        }
        return premiumType ?: PremiumType.NONE
    }

    override fun isTrialPremium(): Boolean {
        val currentAccount = accountManager.activeAccount ?: return false
        return _trialPremiumCache.value[currentAccount.id] == true
    }

    override suspend fun update(): PremiumType = mutex.withLock {
        _premiumCache.value = emptyMap()
        _levelAccountCache.value = emptyMap()

        val currentLevel = userManager.currentUserLevelFlow.value
        if (currentLevel == UserManager.DEFAULT_USER_LEVEL) {
            return PremiumType.NONE
        }
        val parentLevel = getParentLevel(currentLevel)

        // Refresh trial state once per pipeline update, UNCONDITIONALLY. Trial takes display precedence
        // over token premium (see getPremiumTypeForLevel), so an expired trial must be dropped even for an
        // account that also holds token premium — otherwise a stale TRIAL would mask the real state.
        updateTrialPremium()

        // Update current level
        updateForLevel(currentLevel)

        // Update parent level if different
        if (parentLevel != currentLevel) {
            updateForLevel(parentLevel)
        }

        return getPremiumType()
    }

    private suspend fun updateForLevel(level: Int) {
        var accountToCheck = premiumUserRepository.getByLevel(level)

        // Cache level -> accountId mapping for trial premium lookup
        accountToCheck?.let {
            _levelAccountCache.value += (level to it.accountId)
        }

        val cachedResult = try {
            checkCachedPremiumStatus(accountToCheck)
        } catch (_: Exception) {
            accountToCheck = null
            null
        }
        if (cachedResult != null) {
            updateCache(level, cachedResult)
        }

        if (cachedResult == null) {
            val newState = updateAdapterBalanceForLevel(level)
            if (newState?.isPremium() == true) {
                return
            }
        }

        if (cachedResult == null) {
            val premiumStatus = checkPremiumStatusByBalance(accountToCheck, level)
            val result = premiumStatus ?: accountToCheck?.isPremium ?: PremiumType.NONE
            updateCache(level, result)
        }
    }

    private suspend fun updateAdapterBalance(): PremiumType? {
        return updateAdapterBalanceForLevel(userManager.currentUserLevelFlow.value)
    }

    private suspend fun updateAdapterBalanceForLevel(level: Int): PremiumType? {
        when (val adapterResult = checkAdapterPremiumBalanceUseCase()) {
            is CheckAdapterPremiumBalanceUseCase.Result.Premium -> {
                updatePremiumData(
                    address = adapterResult.address,
                    currentLevel = level,
                    coinType = adapterResult.coinType,
                    account = adapterResult.account,
                    premiumType = adapterResult.premiumType
                )
                updateCache(level, adapterResult.premiumType)
                return adapterResult.premiumType
            }

            is CheckAdapterPremiumBalanceUseCase.Result.Insufficient -> {
                updatePremiumData(
                    address = adapterResult.address,
                    currentLevel = level,
                    coinType = adapterResult.coinType,
                    account = adapterResult.account,
                    premiumType = PremiumType.NONE
                )
                updateCache(level, PremiumType.NONE)
            }

            null -> Unit
        }
        return null
    }

    private fun updateCache(level: Int, premiumType: PremiumType) {
        _premiumCache.value += (level to premiumType)
    }

    private suspend fun checkCachedPremiumStatus(accountToCheck: PremiumUser?): PremiumType? {
        if (accountToCheck == null) return null

        if (accountManager.account(accountToCheck.accountId) == null) {
            premiumUserRepository.deleteByAccount(accountToCheck.accountId)
            getBnbAddressUseCase.deleteBnbAddress(accountToCheck.accountId)
            error("Account not found")
        }

        val isWithinCheckInterval = System.currentTimeMillis() - accountToCheck.lastCheckDate <
                PremiumConfig.PREMIUM_CHECK_INTERVAL

        return if (isWithinCheckInterval) accountToCheck.isPremium else null
    }

    private suspend fun checkPremiumStatusByBalance(
        firstAccountToCheck: PremiumUser?,
        currentLevel: Int
    ): PremiumType? {
        val accountsToCheck = getAccountsToCheck(firstAccountToCheck?.accountId)
        deleteRemovedAccounts(accountsToCheck)

        var lastCheckedAddress: String? = null
        var lastCheckedAccount: Account? = null

        var balanceReceived = false // false means no internet connection or no balance received
        for (account in accountsToCheck) {
            if (!account.eligibleForPremium()) continue

            val address = getBnbAddressUseCase.getAddress(account) ?: continue
            lastCheckedAddress = address
            lastCheckedAccount = account

            for (coinType in coinConfigs.keys) {
                val result = isPremiumByBalance(coinType, address)
                balanceReceived = balanceReceived || result != null
                if (result == true) {
                    val premiumType = if (coinType == PremiumConfig.COIN_TYPE_PIRATE) {
                        PremiumType.PIRATE
                    } else {
                        PremiumType.COSA
                    }
                    updatePremiumData(
                        address,
                        currentLevel,
                        coinType,
                        account,
                        premiumType = premiumType
                    )
                    return premiumType
                }
            }
        }

        if (!balanceReceived) {
            // No need to update cache if no balance was received
            return null
        }

        if (lastCheckedAddress != null && lastCheckedAccount != null) {
            // means no premium found for any account
            val coinType = coinConfigs.keys.first()
            updatePremiumData(
                address = lastCheckedAddress,
                currentLevel = currentLevel,
                coinType = coinType,
                account = lastCheckedAccount,
                premiumType = PremiumType.NONE
            )
        }

        return PremiumType.NONE
    }

    private suspend fun deleteRemovedAccounts(accounts: List<Account>) {
        val ids = accounts.map { it.id }
        if (ids.isNotEmpty()) {
            getBnbAddressUseCase.deleteExcludeAccountIds(ids)
        }
    }

    private suspend fun updatePremiumData(
        address: String,
        currentLevel: Int,
        coinType: String,
        account: Account,
        premiumType: PremiumType
    ) {
        premiumUserRepository.insert(
            PremiumUser(
                address = address,
                level = currentLevel,
                coinType = coinType,
                isPremium = premiumType,
                accountId = account.id,
                lastCheckDate = System.currentTimeMillis()
            )
        )
    }

    private fun getAccountsToCheck(primaryAccountId: String?) =
        if (primaryAccountId == null) {
            accountManager.accounts
        } else {
            accountManager.accounts.sortedBy { if (it.id == primaryAccountId) 0 else 1 }
        }

    /**
     * Checks if the user is premium based on their token balance.
     * @param coinType The type of the coin (e.g., "PIRATE", "COSANTA").
     * @param address The wallet address to check.
     * @return Boolean? Returns true if the user is premium, false if not, or null if the balance could not be retrieved.
     */
    private suspend fun isPremiumByBalance(coinType: String, address: String): Boolean? {
        val config = coinConfigs[coinType] ?: return false

        val balance = getTokenBalance(config.contractAddress, address, coinType) ?: return null
        return balance.balance >= config.minAmount.toBigDecimal()
    }

    private suspend fun getTokenBalance(
        contractAddress: String,
        address: String,
        coinType: String
    ): TokenBalance? {
        // Cap the Binance call: it can stall for ~60s on some tokens/addresses. Fail fast to the
        // reliable PiratePlace fallback instead of blocking the whole premium scan.
        val fromBinance = withTimeoutOrNull(BINANCE_BALANCE_TIMEOUT_MS) {
            binanceApi.getTokenBalance(
                contractAddress = contractAddress,
                walletAddress = address
            )
        }
        if (fromBinance != null) return fromBinance
        // Explicit try/catch (NOT runCatching{}.getOrNull()) so coroutine cancellation on this warm-up path
        // is re-thrown rather than swallowed into a null "no balance" result.
        return try {
            TokenBalance(
                balance = piratePlaceRepository.getInvestmentData(coinType, address)
                    .balance.toBigDecimal()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // Trial Premium

    override suspend fun checkTrialPremiumStatus() = withContext(Dispatchers.IO) {
        accountManager.activeAccount?.let {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(it)
        } ?: run {
            TrialPremiumResult.DemoNotFound
        }
    }

    override suspend fun activateTrialPremium(accountId: String): TrialPremiumResult =
        activateTrialPremiumUseCase.activateTrialPremium(accountId).also {
            if (it is TrialPremiumResult.DemoActive) {
                updateTrialPremiumCache(accountId)
            }
        }

    private suspend fun updateTrialPremium() {
        // Build the active-trial set first, then publish it in ONE atomic replace, so an observer of
        // _trialPremiumCache (premiumTypesFlow) never sees a half-rebuilt map. Runs under `mutex` (called
        // from update()), serialized with updateTrialPremiumCache so a concurrent activation is not lost.
        val activeTrialIds = accountManager.accounts
            .filter { checkTrialPremiumUseCase.checkTrialPremiumStatus(it) is TrialPremiumResult.DemoActive }
            .associate { it.id to true }
        _trialPremiumCache.value = activeTrialIds
    }

    private suspend fun updateTrialPremiumCache(accountId: String) {
        // Serialized with updateTrialPremium via the same `mutex`: an optimistic activation added here waits
        // for any in-flight full rebuild and is applied AFTER it, so it is never overwritten. Not reentrant —
        // no caller of updateTrialPremiumCache already holds `mutex` (the warm-up scan uses checkTrial=false).
        // Trial state lives ONLY here — the account cache never pins TRIAL — so an expired trial cannot
        // outlive updateTrialPremium(), and an activated trial wins immediately in premiumTypesFlow.
        mutex.withLock {
            _trialPremiumCache.update { it + (accountId to true) }
        }
    }

    override suspend fun checkPremiumByBalanceForAccount(
        account: Account,
        checkTrial: Boolean
    ): PremiumType = withContext(dispatcherProvider.default) {
        resolvePremium(account, checkTrial).type
    }

    /**
     * Resolves the premium type for a single account. [PremiumResolution.definitive] is false when the
     * result must not be cached — a provider was unavailable, trial resolution errored, the address is
     * not yet known, or the account is not eligible — so a transient failure never pins a wrong value.
     * Trial takes precedence over token balance (in-memory cache first, then a live check).
     */
    private suspend fun resolvePremium(account: Account, checkTrial: Boolean): PremiumResolution {
        // `indeterminate` becomes true when a HIGHER-precedence signal could not be resolved (trial
        // errored, or a higher-priority token balance was unavailable). Any result found below such a
        // gap is still returned for the UI but must not be cached, since the unresolved higher signal
        // could change it once it recovers.
        var indeterminate = false
        if (checkTrial) {
            if (_trialPremiumCache.value[account.id] == true) {
                return PremiumResolution(PremiumType.TRIAL, definitive = true)
            }
            when (checkTrialPremiumUseCase.checkTrialPremiumStatus(account)) {
                is TrialPremiumResult.DemoActive -> {
                    updateTrialPremiumCache(account.id)
                    return PremiumResolution(PremiumType.TRIAL, definitive = true)
                }
                // Trial status unknown: keep checking token balances, but the result is not cacheable.
                is TrialPremiumResult.DemoError -> indeterminate = true
                else -> Unit
            }
        }

        // Eligibility and address availability are re-evaluated on every scan (cheap, not cached) so a
        // later backup or a newly-known hardware address is not hidden behind a cached NONE.
        if (!account.eligibleForPremium()) return PremiumResolution(PremiumType.NONE, definitive = false)
        val address = getBnbAddressUseCase.getAddress(account)
            ?: return PremiumResolution(PremiumType.NONE, definitive = false)

        for (coinType in coinConfigs.keys) {
            when (isPremiumByBalance(coinType, address)) {
                true -> return PremiumResolution(
                    if (coinType == PremiumConfig.COIN_TYPE_PIRATE) PremiumType.PIRATE else PremiumType.COSA,
                    // Definitive only if nothing higher-precedence (trial / a higher-priority token) was unresolved.
                    definitive = !indeterminate
                )

                null -> indeterminate = true
                false -> Unit
            }
        }
        // NONE is definitive only if trial and every balance were actually resolved.
        return PremiumResolution(PremiumType.NONE, definitive = !indeterminate)
    }

    /**
     * TOKEN-only warm-up scan of the given accounts, writing [_accountPremiumCache] (memory + disk) as a
     * side effect. Not part of the public interface — the screens observe [premiumTypesFlow]; this is the
     * background writer (and is exercised directly by unit tests). Returns the token/NONE type per account.
     */
    internal suspend fun getPremiumTypesForAccounts(
        accounts: List<Account>
    ): Map<String, PremiumType> = withContext(dispatcherProvider.default) {
        val result = ConcurrentHashMap<String, PremiumType>()
        coroutineScope {
            accounts.map { account ->
                async {
                    result[account.id] = freshTokenType(account.id) ?: scanAndCache(account)
                }
            }.awaitAll()
        }
        result.toMap()
    }

    /**
     * Runs a single TOKEN-only live check for an account, caps it (INCLUDING the wait for a concurrency
     * permit) with a timeout so queued accounts cannot pile up unbounded, isolates failures to NONE, and
     * caches only a definitive token/NONE result (memory + disk). Runs in the CALLER's coroutine, so a
     * cancelled caller (account change) cancels the check — no detached work outlives it. Uses
     * checkTrial = false so an active trial cannot short-circuit the token rescan; trial is layered on
     * display only (premiumTypesFlow).
     */
    private suspend fun scanAndCache(account: Account): PremiumType {
        val resolution: PremiumResolution? = try {
            withTimeoutOrNull(PREMIUM_CHECK_TIMEOUT_MS) {
                premiumCheckSemaphore.withPermit {
                    // Re-check the TOKEN cache (never trial): a concurrent warm-up may have cached it while
                    // we waited. Trial must not short-circuit the token rescan.
                    freshTokenType(account.id)?.let {
                        return@withPermit PremiumResolution(it, definitive = false)
                    }
                    resolvePremium(account, checkTrial = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Per-account isolation: one account's failure must not fail the whole scan.
            null
        }
        resolution ?: return PremiumType.NONE
        // Cache only definitive token/NONE results (never a timeout/outage) so recovery is picked up next scan.
        if (resolution.definitive) {
            cacheTokenType(account.id, resolution.type)
        }
        return resolution.type
    }

    private suspend fun cacheTokenType(accountId: String, type: PremiumType) {
        _accountPremiumCache.update {
            it + (accountId to CachedPremiumType(type, elapsedRealtimeNanos()))
        }
        persistTokenType(accountId, type)
    }

    private suspend fun persistTokenType(accountId: String, type: PremiumType) {
        // Disk write is best-effort: a failure must not break the scan or the in-memory display. Explicit
        // try/catch (NOT tryOrNull) so coroutine cancellation is re-thrown rather than swallowed.
        try {
            accountPremiumCacheDao.upsert(
                AccountPremiumCacheEntity(accountId, type, System.currentTimeMillis())
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist premium type for account")
        }
    }

    /**
     * The cached TOKEN premium type only if it is fresh — used to decide whether the warm-up can skip a
     * rescan. Trial-agnostic (trial is layered on display in [premiumTypesFlow]) and treats a hydrated
     * entry (checkedAtNanos = null) as not fresh, so it is always rescanned on the launch.
     */
    private fun freshTokenType(accountId: String): PremiumType? {
        val cached = _accountPremiumCache.value[accountId] ?: return null
        val checkedAtNanos = cached.checkedAtNanos ?: return null
        return if (isFresh(checkedAtNanos)) cached.type else null
    }

    // Monotonic clock that also advances during device deep sleep, so neither a wall-clock adjustment
    // nor a sleep period can keep a cache entry alive past its TTL.
    private fun elapsedRealtimeNanos() = SystemClock.elapsedRealtimeNanos()

    private fun isFresh(checkedAtNanos: Long) =
        elapsedRealtimeNanos() - checkedAtNanos < PREMIUM_CHECK_TTL_NANOS
}

internal data class CoinConfig(
    val contractAddress: String,
    val minAmount: Int
)

// checkedAtNanos = null marks a disk-hydrated entry: displayable at any age but never fresh, so the
// warm-up rescans it. A live scan sets it to SystemClock.elapsedRealtimeNanos() for the in-session TTL.
private data class CachedPremiumType(val type: PremiumType, val checkedAtNanos: Long?)

private data class PremiumResolution(val type: PremiumType, val definitive: Boolean)

private const val PREMIUM_CHECK_CONCURRENCY = 8
private const val PREMIUM_CHECK_TIMEOUT_MS = 20_000L
private const val BINANCE_BALANCE_TIMEOUT_MS = 4_000L
private const val PREMIUM_CHECK_TTL_NANOS = PremiumConfig.PREMIUM_CHECK_INTERVAL * 1_000_000L

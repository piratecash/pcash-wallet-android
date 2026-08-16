package cash.p.terminal.core.managers

import com.google.gson.annotations.SerializedName
import cash.p.terminal.R
import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

class RestoreSettingsManager(
    private val storage: IRestoreSettingsStorage,
    private val zcashBirthdayProvider: ZcashBirthdayProvider,
    private val litecoinBirthdayProvider: LitecoinBirthdayProvider,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase
) {
    fun settings(account: Account, blockchainType: BlockchainType): RestoreSettings {
        val records = storage.restoreSettings(account.id, blockchainType.uid)

        val settings = RestoreSettings()
        records.forEach { record ->
            RestoreSettingType.fromString(record.key)?.let { type ->
                settings[type] = record.value
            }
        }

        return settings
    }

    fun accountSettingsInfo(account: Account): List<Triple<BlockchainType, RestoreSettingType, String>> {
        return storage.restoreSettings(account.id).mapNotNull { record ->
            RestoreSettingType.fromString(record.key)?.let { settingType ->
                val blockchainType = BlockchainType.fromUid(record.blockchainTypeUid)
                Triple(blockchainType, settingType, record.value)
            }
        }
    }

    fun save(settings: RestoreSettings, account: Account, blockchainType: BlockchainType) {
        val records = settings.values.map { (type, value) ->
            RestoreSettingRecord(account.id, blockchainType.uid, type.name, value)
        }

        storage.save(
            records + stableTrezorMoneroHeightRecord(
                account = account,
                blockchainType = blockchainType,
                height = settings.birthdayHeight,
            ),
        )
    }

    internal fun trezorMoneroRestoreHeight(walletPublicKey: String): Long? {
        if (walletPublicKey.isBlank()) return null

        return storage.restoreSettings(
            stableTrezorMoneroAccountId(walletPublicKey),
            BlockchainType.Monero.uid,
        )
            .firstOrNull { it.key == RestoreSettingType.BirthdayHeight.name }
            ?.value
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
    }

    internal fun backfillTrezorMoneroRestoreHeights(accounts: List<Account>) {
        accounts.asSequence()
            .mapNotNull { account ->
                val walletPublicKey = (account.type as? AccountType.TrezorDevice)
                    ?.walletPublicKey
                    .orEmpty()
                if (walletPublicKey.isBlank()) return@mapNotNull null

                settings(account, BlockchainType.Monero).birthdayHeight
                    ?.takeIf { it >= 0 }
                    ?.let { height -> walletPublicKey to height }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            .forEach { (walletPublicKey, heights) ->
                if (trezorMoneroRestoreHeight(walletPublicKey) == null) {
                    storage.save(
                        listOf(
                            stableTrezorMoneroHeightRecord(
                                walletPublicKey = walletPublicKey,
                                height = heights.min(),
                            ),
                        ),
                    )
                }
            }
    }

    internal fun savePendingMoneroRescan(account: Account, height: Long) {
        require(height >= 0) { "Monero restore height must be non-negative" }
        storage.save(
            listOf(
                RestoreSettingRecord(
                    account.id,
                    BlockchainType.Monero.uid,
                    RestoreSettingType.BirthdayHeight.name,
                    height.toString(),
                ),
                RestoreSettingRecord(
                    account.id,
                    BlockchainType.Monero.uid,
                    PENDING_MONERO_RESCAN_HEIGHT,
                    height.toString(),
                ),
            ) + stableTrezorMoneroHeightRecord(
                account = account,
                blockchainType = BlockchainType.Monero,
                height = height,
            ),
        )
    }

    internal fun pendingMoneroRescanHeight(account: Account): Long? =
        storage.restoreSettings(account.id, BlockchainType.Monero.uid)
            .firstOrNull { it.key == PENDING_MONERO_RESCAN_HEIGHT }
            ?.value
            ?.toLongOrNull()

    internal fun clearPendingMoneroRescan(account: Account) {
        storage.save(
            listOf(
                RestoreSettingRecord(
                    account.id,
                    BlockchainType.Monero.uid,
                    PENDING_MONERO_RESCAN_HEIGHT,
                    "",
                ),
            ),
        )
    }

    // Missing and legacy values stay pending until hardware-wallet spent status is verified.
    internal fun moneroSpentReconciliationState(account: Account): MoneroSpentReconciliationState {
        val value = storage.restoreSettings(account.id, BlockchainType.Monero.uid)
            .firstOrNull { it.key == MONERO_SPENT_RECONCILIATION_STATE }
            ?.value
        return MoneroSpentReconciliationState.fromPersistedValue(value)
    }

    internal fun saveMoneroSpentReconciliationState(
        account: Account,
        state: MoneroSpentReconciliationState,
    ) {
        require(state.isDurable) { "Migration replay state cannot be persisted" }
        storage.save(
            listOf(
                RestoreSettingRecord(
                    account.id,
                    BlockchainType.Monero.uid,
                    MONERO_SPENT_RECONCILIATION_STATE,
                    checkNotNull(state.persistedValue),
                ),
            ),
        )
    }

    fun getSettingValueForCreatedAccount(settingType: RestoreSettingType, blockchainType: BlockchainType): String? {
        return when (settingType) {
            RestoreSettingType.BirthdayHeight -> {
                when (blockchainType) {
                    BlockchainType.Zcash -> zcashBirthdayProvider.getLatestCheckpointBlockHeight().toString()
                    BlockchainType.Litecoin -> litecoinBirthdayProvider.getLatestCheckpointBlockHeight().toString()
                    BlockchainType.Monero -> validateMoneroHeightUseCase.getTodayHeight().toString()
                    else -> null
                }
            }
        }
    }

    fun getSettingsTitle(settingType: RestoreSettingType, token: Token): String {
        return when (settingType) {
            RestoreSettingType.BirthdayHeight -> cash.p.terminal.strings.helpers.Translator.getString(R.string.ManageAccount_BirthdayHeight, token.coin.code)
        }
    }

    private companion object {
        const val TREZOR_MONERO_ACCOUNT_PREFIX = "trezor-monero:"
        const val PENDING_MONERO_RESCAN_HEIGHT = "monero_hardware_rescan_pending_height"
        const val MONERO_SPENT_RECONCILIATION_STATE = "monero_spent_reconciliation_state"
    }

    private fun stableTrezorMoneroHeightRecord(
        account: Account,
        blockchainType: BlockchainType,
        height: Long?,
    ): List<RestoreSettingRecord> {
        if (blockchainType != BlockchainType.Monero || height == null || height < 0) {
            return emptyList()
        }
        val walletPublicKey = (account.type as? AccountType.TrezorDevice)
            ?.walletPublicKey
            .orEmpty()
        if (walletPublicKey.isBlank()) return emptyList()

        return listOf(
            stableTrezorMoneroHeightRecord(walletPublicKey, height),
        )
    }

    private fun stableTrezorMoneroHeightRecord(
        walletPublicKey: String,
        height: Long,
    ) = RestoreSettingRecord(
        stableTrezorMoneroAccountId(walletPublicKey),
        BlockchainType.Monero.uid,
        RestoreSettingType.BirthdayHeight.name,
        height.toString(),
    )

    private fun stableTrezorMoneroAccountId(walletPublicKey: String): String =
        TREZOR_MONERO_ACCOUNT_PREFIX + walletPublicKey
}

internal enum class MoneroSpentReconciliationState(
    val persistedValue: String?,
) {
    Ready("READY:v2"),
    LiveRefreshPending("LIVE_REFRESH_PENDING:v2"),
    MigrationReplayPending("MIGRATION_REPLAY_PENDING:v2"),
    ExplicitColdRecoveryPending("EXPLICIT_COLD_RECOVERY_PENDING:v2"),
    MigrationReplayRequired(null),
    ;

    val isDurable: Boolean
        get() = persistedValue != null

    companion object {
        fun fromPersistedValue(value: String?): MoneroSpentReconciliationState =
            if (value == "EXPLICIT_COLD_RECOVERY_PENDING:v1") ExplicitColdRecoveryPending
            else entries.firstOrNull { it.persistedValue == value && it.isDurable }
                ?: MigrationReplayRequired
    }
}

enum class RestoreSettingType {
    @SerializedName("birthday_height")
    BirthdayHeight;

    companion object {
        private val map = values().associateBy(RestoreSettingType::name)

        fun fromString(value: String?): RestoreSettingType? = map[value]
    }
}

class RestoreSettings {
    val values = mutableMapOf<RestoreSettingType, String>()

    var birthdayHeight: Long?
        get() = values[RestoreSettingType.BirthdayHeight]?.toLongOrNull()
        set(value) {
            values[RestoreSettingType.BirthdayHeight] = value?.toString() ?: ""
        }

    fun isNotEmpty() = values.isNotEmpty()

    operator fun get(key: RestoreSettingType): String? {
        return values[key]
    }

    operator fun set(key: RestoreSettingType, value: String) {
        values[key] = value
    }
}

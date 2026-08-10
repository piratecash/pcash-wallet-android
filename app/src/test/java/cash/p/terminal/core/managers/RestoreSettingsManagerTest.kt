package cash.p.terminal.core.managers

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RestoreSettingsManagerTest {

    private val storage = mockk<IRestoreSettingsStorage>(relaxed = true)
    private val zcashBirthdayProvider = mockk<ZcashBirthdayProvider>(relaxed = true)
    private val litecoinBirthdayProvider = mockk<LitecoinBirthdayProvider>()
    private val validateMoneroHeightUseCase = mockk<ValidateMoneroHeightUseCase>(relaxed = true)

    @Test
    fun getSettingValueForCreatedAccount_litecoinBirthdayHeight_returnsLatestCheckpoint() {
        every { litecoinBirthdayProvider.getLatestCheckpointBlockHeight() } returns LITECOIN_CHECKPOINT
        val manager = createManager()

        val value = manager.getSettingValueForCreatedAccount(
            RestoreSettingType.BirthdayHeight,
            BlockchainType.Litecoin
        )

        assertEquals(LITECOIN_CHECKPOINT.toString(), value)
    }

    @Test
    fun pendingMoneroRescan_saveAndClear_preservesBirthdayHeight() {
        val records = mutableMapOf<Pair<String, String>, RestoreSettingRecord>()
        every {
            storage.restoreSettings(ACCOUNT.id, BlockchainType.Monero.uid)
        } answers {
            records.values.filter { it.accountId == ACCOUNT.id }
        }
        every { storage.save(any()) } answers {
            firstArg<List<RestoreSettingRecord>>().forEach { record ->
                records[record.accountId to record.key] = record
            }
        }
        val manager = createManager()

        manager.savePendingMoneroRescan(ACCOUNT, MONERO_HEIGHT)

        assertEquals(MONERO_HEIGHT, manager.pendingMoneroRescanHeight(ACCOUNT))
        assertEquals(
            MONERO_HEIGHT,
            manager.settings(ACCOUNT, BlockchainType.Monero).birthdayHeight,
        )
        verify(exactly = 1) {
            storage.save(
                match { saved ->
                    saved.count { it.accountId == ACCOUNT.id } == 2 &&
                        saved.any {
                            it.key == RestoreSettingType.BirthdayHeight.name &&
                                it.value == MONERO_HEIGHT.toString()
                        } &&
                        saved.any {
                            it.accountId != ACCOUNT.id &&
                                it.key == RestoreSettingType.BirthdayHeight.name &&
                                it.value == MONERO_HEIGHT.toString()
                        }
                },
            )
        }

        manager.clearPendingMoneroRescan(ACCOUNT)

        assertEquals(null, manager.pendingMoneroRescanHeight(ACCOUNT))
        assertEquals(
            MONERO_HEIGHT,
            manager.settings(ACCOUNT, BlockchainType.Monero).birthdayHeight,
        )
    }

    @Test
    fun save_trezorMoneroHeight_canBeRestoredByStableWalletIdentity() {
        val records = mutableMapOf<Triple<String, String, String>, RestoreSettingRecord>()
        every { storage.restoreSettings(any(), any()) } answers {
            val accountId = firstArg<String>()
            val blockchainUid = secondArg<String>()
            records.values.filter {
                it.accountId == accountId && it.blockchainTypeUid == blockchainUid
            }
        }
        every { storage.save(any()) } answers {
            firstArg<List<RestoreSettingRecord>>().forEach { record ->
                records[
                    Triple(record.accountId, record.blockchainTypeUid, record.key)
                ] = record
            }
        }
        val manager = createManager()
        val settings = RestoreSettings().apply {
            birthdayHeight = MONERO_HEIGHT
        }

        manager.save(settings, ACCOUNT, BlockchainType.Monero)

        assertEquals(
            MONERO_HEIGHT,
            manager.trezorMoneroRestoreHeight("wallet-key"),
        )
        verify(exactly = 1) {
            storage.save(
                match { saved ->
                    saved.any {
                        it.accountId != ACCOUNT.id &&
                            it.blockchainTypeUid == BlockchainType.Monero.uid &&
                            it.key == RestoreSettingType.BirthdayHeight.name &&
                            it.value == MONERO_HEIGHT.toString()
                    }
                },
            )
        }
    }

    @Test
    fun save_nonTrezorMoneroHeight_doesNotCreateStableIdentityRecord() {
        val manager = createManager()
        val settings = RestoreSettings().apply {
            birthdayHeight = MONERO_HEIGHT
        }
        val account = ACCOUNT.copy(type = AccountType.Mnemonic(words = listOf("word"), passphrase = ""))

        manager.save(settings, account, BlockchainType.Monero)

        verify(exactly = 1) {
            storage.save(
                match { saved ->
                    saved.size == 1 && saved.single().accountId == account.id
                },
            )
        }
    }

    @Test
    fun backfillTrezorMoneroRestoreHeights_legacyAccounts_preserveEarliestHeightByWalletIdentity() {
        val records = mutableMapOf<Triple<String, String, String>, RestoreSettingRecord>()
        val newerAccount = ACCOUNT.copy(id = "newer-account")
        records[
            Triple(
                ACCOUNT.id,
                BlockchainType.Monero.uid,
                RestoreSettingType.BirthdayHeight.name,
            )
        ] = RestoreSettingRecord(
            ACCOUNT.id,
            BlockchainType.Monero.uid,
            RestoreSettingType.BirthdayHeight.name,
            MONERO_HEIGHT.toString(),
        )
        records[
            Triple(
                newerAccount.id,
                BlockchainType.Monero.uid,
                RestoreSettingType.BirthdayHeight.name,
            )
        ] = RestoreSettingRecord(
            newerAccount.id,
            BlockchainType.Monero.uid,
            RestoreSettingType.BirthdayHeight.name,
            (MONERO_HEIGHT + 10_000).toString(),
        )
        every { storage.restoreSettings(any(), any()) } answers {
            val accountId = firstArg<String>()
            val blockchainUid = secondArg<String>()
            records.values.filter {
                it.accountId == accountId && it.blockchainTypeUid == blockchainUid
            }
        }
        every { storage.save(any()) } answers {
            firstArg<List<RestoreSettingRecord>>().forEach { record ->
                records[
                    Triple(record.accountId, record.blockchainTypeUid, record.key)
                ] = record
            }
        }
        val manager = createManager()

        manager.backfillTrezorMoneroRestoreHeights(listOf(newerAccount, ACCOUNT))

        assertEquals(
            MONERO_HEIGHT,
            manager.trezorMoneroRestoreHeight("wallet-key"),
        )
    }

    @Test
    fun moneroSpentReconciliation_absentAndLegacy_failClosedAsMigrationReplayRequired() {
        every { storage.restoreSettings(ACCOUNT.id, BlockchainType.Monero.uid) } returns emptyList()
        val manager = createManager()

        assertEquals(
            MoneroSpentReconciliationState.MigrationReplayRequired,
            manager.moneroSpentReconciliationState(ACCOUNT),
        )

        listOf(
            "READY:v0",
            "READY:v1",
            "LIVE_REFRESH_PENDING:v1",
            "MIGRATION_REPLAY_PENDING:v1",
        ).forEach { value ->
            every { storage.restoreSettings(ACCOUNT.id, BlockchainType.Monero.uid) } returns listOf(
                RestoreSettingRecord(
                    ACCOUNT.id,
                    BlockchainType.Monero.uid,
                    "monero_spent_reconciliation_state",
                    value,
                ),
            )

            assertEquals(
                MoneroSpentReconciliationState.MigrationReplayRequired,
                manager.moneroSpentReconciliationState(ACCOUNT),
            )
        }
    }

    @Test
    fun moneroSpentReconciliation_persistsOnlyDurableVersionedValues() {
        val manager = createManager()

        manager.saveMoneroSpentReconciliationState(ACCOUNT, MoneroSpentReconciliationState.LiveRefreshPending)
        manager.saveMoneroSpentReconciliationState(ACCOUNT, MoneroSpentReconciliationState.Ready)

        verify(exactly = 1) {
            storage.save(match { records ->
                records.singleOrNull()?.let { record ->
                    record.value == "LIVE_REFRESH_PENDING:v2" &&
                        record.key == "monero_spent_reconciliation_state"
                } == true
            })
        }
        verify(exactly = 1) {
            storage.save(match { records ->
                records.singleOrNull()?.let { record ->
                    record.value == "READY:v2" &&
                        record.key == "monero_spent_reconciliation_state"
                } == true
            })
        }
    }

    @Test
    fun moneroSpentReconciliation_migrationReplayRequired_cannotBePersisted() {
        val manager = createManager()

        assertFailsWith<IllegalArgumentException> {
            manager.saveMoneroSpentReconciliationState(
                ACCOUNT,
                MoneroSpentReconciliationState.MigrationReplayRequired,
            )
        }

        verify(exactly = 0) { storage.save(any()) }
    }

    @Test
    fun moneroSpentReconciliation_currentGenerationRoundTripsAndPreservesExplicitRecovery() {
        val manager = createManager()

        listOf(
            "READY:v2" to MoneroSpentReconciliationState.Ready,
            "LIVE_REFRESH_PENDING:v2" to MoneroSpentReconciliationState.LiveRefreshPending,
            "MIGRATION_REPLAY_PENDING:v2" to MoneroSpentReconciliationState.MigrationReplayPending,
            "EXPLICIT_COLD_RECOVERY_PENDING:v2" to MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
            "EXPLICIT_COLD_RECOVERY_PENDING:v1" to MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
        ).forEach { (value, expected) ->
            every { storage.restoreSettings(ACCOUNT.id, BlockchainType.Monero.uid) } returns listOf(
                RestoreSettingRecord(
                    ACCOUNT.id,
                    BlockchainType.Monero.uid,
                    "monero_spent_reconciliation_state",
                    value,
                ),
            )

            assertEquals(expected, manager.moneroSpentReconciliationState(ACCOUNT))
        }
    }

    private fun createManager() = RestoreSettingsManager(
        storage = storage,
        zcashBirthdayProvider = zcashBirthdayProvider,
        litecoinBirthdayProvider = litecoinBirthdayProvider,
        validateMoneroHeightUseCase = validateMoneroHeightUseCase,
    )

    private companion object {
        const val LITECOIN_CHECKPOINT = 3_000_000L
        const val MONERO_HEIGHT = 3_529_956L
        val ACCOUNT = Account(
            id = "account-id",
            name = "Trezor",
            type = AccountType.TrezorDevice(
                deviceId = "device-id",
                model = "T3T1",
                firmwareVersion = "2.8.10",
                walletPublicKey = "wallet-key",
            ),
            origin = AccountOrigin.Created,
            level = 0,
        )
    }
}

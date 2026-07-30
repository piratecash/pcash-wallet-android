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
        val records = mutableMapOf<String, RestoreSettingRecord>()
        every {
            storage.restoreSettings(ACCOUNT.id, BlockchainType.Monero.uid)
        } answers {
            records.values.toList()
        }
        every { storage.save(any()) } answers {
            firstArg<List<RestoreSettingRecord>>().forEach { record ->
                records[record.key] = record
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
                    saved.size == 2 &&
                        saved.any {
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

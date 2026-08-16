package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.SecretString
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class MoneroDeviceWalletProvisioner(
    private val moneroFileDao: MoneroFileDao,
    private val nativeWallet: MoneroDeviceWalletNative,
    private val nativeRuntime: MoneroNativeWalletRuntime,
    private val files: MoneroDeviceWalletFileStore,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val operationMutex = Mutex()

    suspend fun provision(
        account: Account,
        restoreHeight: Long,
        onWalletCreated: () -> Unit = {},
    ): String = withContext(dispatcherProvider.io) {
        require(restoreHeight >= 0) { "Monero restore height must be non-negative" }
        operationMutex.withLock {
            existingWalletPublicKey(account, restoreHeight)?.let { walletPublicKey ->
                return@withLock walletPublicKey
            }

            val baseName = files.baseName(account.id)
            var attemptStarted = false
            var recordInserted = false
            try {
                val identity = nativeRuntime.withExclusiveWallet {
                    nativeWallet.requireAvailable()
                    attemptStarted = true
                    files.prepare(baseName)
                    nativeWallet.create(
                        walletFile = files.walletFile(baseName),
                        password = files.password,
                        restoreHeight = restoreHeight,
                        account = account,
                    )
                }
                check(files.valid(baseName)) { "Monero device wallet files are incomplete" }
                onWalletCreated()
                moneroFileDao.insert(
                    MoneroFileRecord(
                        accountId = account.id,
                        fileName = SecretString(baseName),
                        password = SecretString(files.password),
                    ),
                )
                recordInserted = true
                saveRestoreHeight(account, restoreHeight)
                restoreSettingsManager.saveMoneroSpentReconciliationState(
                    account,
                    MoneroSpentReconciliationState.LiveRefreshPending,
                )
                identity
            } catch (error: MoneroDeviceWalletOwnershipRetainedException) {
                throw error
            } catch (error: Throwable) {
                if (attemptStarted) {
                    cleanupFailedProvisioning(baseName, account.id, recordInserted, error)
                }
                throw error
            }
        }
    }

    private suspend fun cleanupFailedProvisioning(
        baseName: String,
        accountId: String,
        recordInserted: Boolean,
        error: Throwable,
    ) = withContext(NonCancellable) {
        if (recordInserted) {
            moneroFileDao.deleteAssociatedRecord(accountId)
        }
        if (!files.cleanup(baseName)) {
            error.addSuppressed(
                IllegalStateException("Unable to remove incomplete Monero wallet files"),
            )
        }
    }

    private suspend fun existingWalletPublicKey(
        account: Account,
        restoreHeight: Long,
    ): String? {
        val record = moneroFileDao.getAssociatedRecord(account.id) ?: return null
        if (!files.valid(record.fileName.value)) {
            throw provisioningFailure("Monero device wallet files are incomplete")
        }
        val walletPublicKey = (account.type as? AccountType.TrezorDevice)
            ?.walletPublicKey
            .orEmpty()
        if (walletPublicKey.isNotEmpty()) {
            saveRestoreHeight(account, restoreHeight, onlyIfMissing = true)
            return walletPublicKey
        }
        moneroFileDao.deleteAssociatedRecord(account.id)
        return null
    }

    private fun saveRestoreHeight(
        account: Account,
        restoreHeight: Long,
        onlyIfMissing: Boolean = false,
    ) {
        val settings = restoreSettingsManager.settings(account, MONERO_BLOCKCHAIN)
        if (onlyIfMissing && settings.birthdayHeight != null) return
        settings.birthdayHeight = restoreHeight
        restoreSettingsManager.save(settings, account, MONERO_BLOCKCHAIN)
    }

    private fun provisioningFailure(detail: String) = HardwareWalletOperationException(
        HardwareWalletErrorCode.IncompleteCreation,
        detail,
    )

    private companion object {
        val MONERO_BLOCKCHAIN = BlockchainType.Monero
    }
}

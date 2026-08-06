package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.useCases.MoneroWalletFiles
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.util.Helper
import com.m2049r.xmrwallet.util.KeyStoreHelper
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import java.io.File

internal interface MoneroDeviceWalletNative {
    fun requireAvailable()

    suspend fun create(
        walletFile: File,
        password: String,
        restoreHeight: Long,
        account: Account,
    ): String
}

internal class DefaultMoneroDeviceWalletNative(
    private val gateway: MoneroTrezorOperationGateway,
) : MoneroDeviceWalletNative {
    override fun requireAvailable() {
        check(WalletManager.getInstance().wallet == null) { "A Monero wallet is already open" }
    }

    override suspend fun create(
        walletFile: File,
        password: String,
        restoreHeight: Long,
        account: Account,
    ): String = gateway.execute(account) { walletPublicKey ->
        val manager = WalletManager.getInstance()
        val wallet = manager.createWalletFromDevice(
            walletFile,
            password,
            restoreHeight,
            Wallet.Device.Trezor,
        )
        var walletFaulted = false
        useWallet(manager, wallet, walletFaulted = { walletFaulted }) {
            val status = wallet.status
            if (!status.isOk) {
                throw HardwareWalletOperationException(
                    status.hardwareWalletError ?: HardwareWalletErrorCode.Protocol,
                    status.errorString,
                )
            }
            storeMoneroWalletSafely(
                failureMessage = "Failed to store Monero device wallet",
                store = wallet::storeSafe,
                onNativeFault = { _ ->
                    walletFaulted = true
                    manager.clearManagedWalletIfCurrent(wallet)
                },
            )
            walletPublicKey
        }
    }

    private fun <T> useWallet(
        manager: WalletManager,
        wallet: Wallet,
        walletFaulted: () -> Boolean,
        block: () -> T,
    ): T = useMoneroDeviceWallet(
        close = {
            if (walletFaulted()) {
                true
            } else {
                manager.close(wallet, false)
            }
        },
        closeFailure = {
            wallet.status.toHardwareWalletCloseFailure(
                "Failed to close Monero device wallet",
            )
        },
        block = block,
    )
}

internal fun storeMoneroWalletSafely(
    failureMessage: String,
    store: () -> Int,
    onNativeFault: (HardwareWalletOperationException) -> Unit,
) {
    val status = store()
    if (status == MONERO_STORE_OK) return
    val failure = HardwareWalletOperationException(
        HardwareWalletErrorCode.StoreFailed,
        failureMessage,
    )
    if (status == MONERO_STORE_NATIVE_FAULT) {
        onNativeFault(failure)
    }
    throw failure
}

internal const val MONERO_STORE_OK = 0
internal const val MONERO_STORE_NATIVE_FAULT = 2

internal class MoneroDeviceWalletOwnershipRetainedException(
    cause: Throwable,
) : RuntimeException(cause.message, cause)

internal fun <T> useMoneroDeviceWallet(
    close: () -> Boolean,
    closeFailure: () -> Throwable,
    block: () -> T,
): T {
    val result = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }
    closeMoneroDeviceWallet(close, closeFailure)?.let { failure ->
        throwOwnershipRetained(result.exceptionOrNull(), failure)
    }
    return result.getOrThrow()
}

private fun throwOwnershipRetained(
    operationFailure: Throwable?,
    closeFailure: Throwable,
): Nothing {
    val cause = operationFailure?.apply { addSuppressed(closeFailure) } ?: closeFailure
    throw MoneroDeviceWalletOwnershipRetainedException(cause)
}

private fun closeMoneroDeviceWallet(
    close: () -> Boolean,
    closeFailure: () -> Throwable,
): Throwable? =
    try {
        if (close()) null else closeFailure()
    } catch (error: Throwable) {
        error
    }

internal fun Wallet.Status?.toHardwareWalletCloseFailure(
    fallbackDetail: String,
) = HardwareWalletOperationException(
    this?.hardwareWalletError ?: HardwareWalletErrorCode.Protocol,
    this?.errorString?.takeIf(String::isNotBlank) ?: fallbackDetail,
)

internal class MoneroDeviceWalletFileStore private constructor(
    private val root: File,
    private val passwordProvider: () -> String,
) {
    val password: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED, passwordProvider)

    fun baseName(accountId: String): String = "trezor-$accountId"

    fun walletFile(baseName: String): File = files(baseName).cache

    fun prepare(baseName: String) {
        require(root.isDirectory || root.mkdirs()) { "Monero wallet directory is unavailable" }
        check(cleanup(baseName)) { "Unable to remove incomplete Monero wallet files" }
    }

    fun valid(baseName: String): Boolean =
        files(baseName).required.all { it.isFile && it.length() > 0 }

    fun cleanup(baseName: String): Boolean =
        files(baseName).all.fold(true) { allDeleted, file ->
            (!file.exists() || file.delete()) && allDeleted
        }

    private fun files(baseName: String) = MoneroWalletFiles(root.resolve(baseName))

    companion object {
        fun create(context: Context) = MoneroDeviceWalletFileStore(
            root = Helper.getWalletRoot(context),
            passwordProvider = { KeyStoreHelper.getCrazyPass(context, "") },
        )

        internal fun create(root: File, password: String) =
            MoneroDeviceWalletFileStore(root) { password }
    }
}

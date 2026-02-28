package cash.p.terminal.core.usecase

import android.content.Context
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.core.utils.MoneroWalletSeedConverter
import cash.p.terminal.wallet.AccountType
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.m2049r.xmrwallet.util.Helper
import com.m2049r.xmrwallet.util.KeyStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MoneroWalletUseCase(
    private val appContext: Context,
    private val moneroWalletService: MoneroWalletService,
    private val generateMoneroWalletUseCase: GenerateMoneroWalletUseCase
) {
    suspend fun createNew(): AccountType? = withContext(Dispatchers.IO) {
        val walletInnerName = generateMoneroWalletUseCase() ?: return@withContext null

        // check if the wallet we want to create already exists
        val walletFolder: File = Helper.getWalletRoot(appContext)
        if (!walletFolder.isDirectory()) {
            Timber.e("Wallet dir " + walletFolder.absolutePath + "is not a directory")
            return@withContext null
        }
        val cacheFile = File(walletFolder, walletInnerName)
        val keysFile = File(walletFolder, "$walletInnerName.keys")
        val addressFile = File(walletFolder, "$walletInnerName.address.txt")

        if (cacheFile.exists() || keysFile.exists() || addressFile.exists()) {
            Timber.e("Some wallet files already exist for %s", cacheFile.absolutePath)
            return@withContext null
        }

        val newWalletFile = File(walletFolder, walletInnerName)
        val crazyPass = KeyStoreHelper.getCrazyPass(appContext, "")
        val currentNode = MoneroConfig.autoSelectNode()

        // get it from the connected node if we have one
        val restoreHeight = if (currentNode?.testRpcService() == true) {
            currentNode.height
        } else {
            -1
        }
        closeOpenedWallet()
        val newWallet = WalletManager.getInstance()
            .createWallet(
                newWalletFile,
                crazyPass,
                "English",
                restoreHeight
            )
        val walletStatus: Wallet.Status = newWallet.status
        if (!walletStatus.isOk) {
            Timber.e(walletStatus.errorString)
        }
        val words = newWallet.getSeed("").split(" ")
        val height = newWallet.restoreHeight
        newWallet.close()

        return@withContext if (walletStatus.isOk) {
            AccountType.MnemonicMonero(
                words = words,
                password = crazyPass,
                height = height,
                walletInnerName = walletInnerName
            )
        } else {
            null
        }
    }

    suspend fun copyWalletFiles(fromAccount: AccountType.MnemonicMonero): AccountType.MnemonicMonero? =
        withContext(Dispatchers.IO) {
            val newWalletInnerName = generateMoneroWalletUseCase() ?: return@withContext null

            val walletFolder: File = Helper.getWalletRoot(appContext)
            if (!walletFolder.isDirectory()) {
                Timber.e("Wallet dir " + walletFolder.absolutePath + "is not a directory")
                return@withContext null
            }

            return@withContext if (copyFiles(
                    walletFolder,
                    fromAccount.walletInnerName,
                    newWalletInnerName
                )
            ) {
                AccountType.MnemonicMonero(
                    words = fromAccount.words,
                    password = fromAccount.password,
                    height = fromAccount.height,
                    walletInnerName = newWalletInnerName
                )
            } else {
                null
            }
        }

    private suspend fun copyFiles(
        dir: File,
        fromWalletInnerName: String,
        newWalletInnerName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!dir.isDirectory) return@withContext false

        dir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("$fromWalletInnerName.")) {
                val extension = name.substringAfter('.', "")
                val newName = if (extension.isNotEmpty()) {
                    "$newWalletInnerName.$extension"
                } else {
                    newWalletInnerName
                }
                val target = File(dir, newName)
                if (target.exists()) {
                    Timber.d("Target file $target already exists, skipping copy")
                    return@withContext false
                }
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return@withContext true
    }

    suspend fun restoreFromBip39(
        words: List<String>,
        passphrase: String,
        height: Long
    ): AccountType.MnemonicMonero? =
        restore(MoneroWalletSeedConverter.getLegacySeedFromBip39(words, passphrase), height)

    suspend fun restore(
        words: List<String>,
        height: Long,
        crazyPassExisting: String? = null,
        walletInnerNameExisting: String? = null
    ): AccountType.MnemonicMonero? =
        withContext(Dispatchers.IO) {
            if (words.size != MoneroConfig.WORD_COUNT) {
                Timber.d("Wrong words count")
            }

            val walletInnerName =
                walletInnerNameExisting ?: generateMoneroWalletUseCase() ?: return@withContext null

            val crazyPass = crazyPassExisting ?: KeyStoreHelper.getCrazyPass(appContext, "")
            val walletFolder: File = Helper.getWalletRoot(appContext)
            val newWalletFile = File(walletFolder, walletInnerName)
            closeOpenedWallet()
            val newWallet = WalletManager.getInstance()
                .recoveryWallet(newWalletFile, crazyPass, words.joinToString(" "), "", height)

            val walletFile = File(walletFolder, walletInnerName)
            Timber.d("New Wallet %s", walletFile.absolutePath)
            // NEXT line is VERY important for correct update
            walletFile.delete() // when recovering wallets, the cache seems corrupt - so remove it

            val walletStatus: Wallet.Status = newWallet.status
            if (!walletStatus.isOk) {
                Timber.e(walletStatus.errorString)
            }
            return@withContext if (walletStatus.isOk) {
                AccountType.MnemonicMonero(
                    words = words,
                    password = crazyPass,
                    height = height,
                    walletInnerName = walletInnerName
                )
            } else {
                null
            }
        }

    private fun closeOpenedWallet() {
        tryOrNull { moneroWalletService.stop(false) }
    }
}
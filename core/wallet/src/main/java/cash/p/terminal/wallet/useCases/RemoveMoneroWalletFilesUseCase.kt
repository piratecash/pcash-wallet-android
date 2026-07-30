package cash.p.terminal.wallet.useCases

import android.content.Context
import cash.p.terminal.wallet.Account
import com.m2049r.xmrwallet.util.Helper
import com.m2049r.xmrwallet.util.KeyStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RemoveMoneroWalletFilesUseCase(
    private val appContext: Context,
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase
) {

    suspend operator fun invoke(account: Account): Boolean = withContext(Dispatchers.IO) {
        getMoneroWalletFilesNameUseCase(account)?.let { walletInnerName ->
            invoke(walletInnerName)
        } ?: run {
            false
        }
    }

    suspend operator fun invoke(walletInnerName: String): Boolean = withContext(Dispatchers.IO) {
        val file = Helper.getWalletFile(appContext, walletInnerName)
        deleteWallet(file)
    }

    private fun deleteWallet(walletFile: File): Boolean {
        val files = MoneroWalletFiles(walletFile)
        var success = true
        if (files.cache.exists()) {
            success = files.cache.delete()
        }
        success = files.keys.delete() && success
        if (files.address.exists()) {
            success = files.address.delete() && success
        }
        KeyStoreHelper.removeWalletUserPass(appContext, walletFile.name)
        return success
    }
}

class MoneroWalletFiles(walletFile: File) {
    val cache = walletFile
    val keys = walletFile.resolveSibling("${walletFile.name}.keys")
    val address = walletFile.resolveSibling("${walletFile.name}.address.txt")
    val required = listOf(cache, keys)
    val all = required + address
}

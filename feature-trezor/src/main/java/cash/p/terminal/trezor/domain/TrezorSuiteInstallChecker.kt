package cash.p.terminal.trezor.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class TrezorSuiteInstallChecker(
    private val context: Context
) {
    companion object {
        private const val TREZOR_SUITE_PACKAGE = "io.trezor.suite"
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$TREZOR_SUITE_PACKAGE"
    }

    fun isInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TREZOR_SUITE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getPlayStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

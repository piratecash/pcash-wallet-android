package cash.p.terminal.modules.softwareupdate.domain

import android.os.Build
import cash.p.terminal.core.App
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.network.github.domain.entity.AppRelease

interface InstallSourceProvider {
    val installSource: InstallSource

    /** Where "Update now" should send the user, based on how the app was installed. */
    fun updateDestinationUrl(release: AppRelease): String
}

class InstallSourceProviderImpl : InstallSourceProvider {

    override val installSource: InstallSource
        get() = when (installerPackage()) {
            GOOGLE_PLAY_PACKAGE -> InstallSource.GOOGLE_PLAY
            in FDROID_PACKAGES -> InstallSource.FDROID
            else -> InstallSource.OTHER
        }

    override fun updateDestinationUrl(release: AppRelease): String {
        val packageName = "cash.p.terminal" // use always release apk package
        return when (installSource) {
            InstallSource.GOOGLE_PLAY -> "https://play.google.com/store/apps/details?id=$packageName"
            InstallSource.FDROID -> "https://f-droid.org/packages/$packageName/"
            InstallSource.OTHER -> release.htmlUrl
        }
    }

    private fun installerPackage(): String? {
        val context = App.instance
        val packageName = context.packageName
        return tryOrNull {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
        }
    }

    private companion object {
        const val GOOGLE_PLAY_PACKAGE = "com.android.vending"
        val FDROID_PACKAGES = setOf("org.fdroid.fdroid", "org.fdroid.basic")
    }
}

package cash.p.terminal.core.managers

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.strings.helpers.Translator
import io.horizontalsystems.core.ISystemInfoManager
import java.security.MessageDigest

class SystemInfoManager(
    private val localStorage: ILocalStorage,
    private val context: Context
) : ISystemInfoManager {

    override val appVersion: String = AppConfigProvider.appVersion

    override val appVersionFull: String by lazy {
        var version = AppConfigProvider.appVersion

        // Add git hash suffix
        val gitHash = AppConfigProvider.appGitHash
        if (gitHash.isNotEmpty()) {
            version += "-$gitHash"
        }

        version
    }

    override val appVersionDisplay: String by lazy {
        buildString {
            append(appVersionFull)
            if (Translator.getString(R.string.is_release) == "false") {
                append(" (${AppConfigProvider.appBuild})")
            }
            val branch = AppConfigProvider.appGitBranch
            if (branch.isNotEmpty() && branch != "master" && branch != "f-droid" && branch != "unknown") {
                append(" [$branch]")
            }
        }
    }

    private val biometricManager by lazy { BiometricManager.from(App.instance) }

    override val isDeviceSecure: Boolean
        get() {
            val keyguardManager =
                App.instance.getSystemService(Activity.KEYGUARD_SERVICE) as KeyguardManager
            return keyguardManager.isDeviceSecure
        }

    override val isSystemLockOff: Boolean
        get() {
            // No need to check a secure device if system pin is not required
            if (!localStorage.isSystemPinRequired) return false

            return !isDeviceSecure
        }

    override val biometricAuthSupported: Boolean
        get() = biometricManager.canAuthenticate(BIOMETRIC_WEAK) == BIOMETRIC_SUCCESS

    override val deviceModel: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    override val osVersion: String
        get() = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"


    override fun getSigningCertFingerprint(): String? {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName

            val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo

                when {
                    signingInfo?.hasMultipleSigners() == true ->
                        signingInfo.apkContentsSigners?.firstOrNull()
                    else ->
                        signingInfo?.signingCertificateHistory?.firstOrNull()
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull()
            }

            cert?.let {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(it.toByteArray())
                digest.joinToString(":") { byte -> "%02X".format(byte) }
            }
        } catch (e: Exception) {
            null
        }
    }


}

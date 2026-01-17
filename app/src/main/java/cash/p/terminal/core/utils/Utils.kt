package cash.p.terminal.core.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.delay
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.MessageDigest

object Utils {

    fun isUsingCustomKeyboard(context: Context): Boolean {

        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethodProperties = inputMethodManager.enabledInputMethodList
        for (i in 0 until inputMethodProperties.size) {
            val imi = inputMethodProperties[i]
            if (imi.id == Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)) {
                if ((imi.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    return true
                }
            }
        }

        return false
    }

    suspend fun waitUntil(timeout: Long, checkPeriod: Long, condition: () -> Boolean) {
        var waited = 0L
        while (!condition.invoke() && waited < timeout) {
            delay(checkPeriod)
            waited += checkPeriod
        }
    }

    fun getIpByUrl(host: String): List<String>? = try {
        InetAddress
            .getAllByName(host)
            .filter { it !is Inet6Address }
            .mapNotNull { it.hostAddress }
    } catch (e: UnknownHostException) {
        Log.d("Utils", "getIpByUrl: $host not found")
        null
    }

    fun getSigningCertFingerprint(context: Context): String? {
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

object EthInputParser {

    fun parse(input: String): InputData? {

        val transferIndex = input.indexOf("a9059cbb")
        if (transferIndex > -1) {
            val startIndex = transferIndex + 8
            val startAddress = startIndex + 24
            val endAddress = startAddress + 40

            val address = input.substring(startAddress, endAddress)
            val amount = input.substring(endAddress, endAddress + 64)

            return InputData(from = null, to = address, value = amount)
        }

        val transferFromIndex = input.indexOf("23b872dd")
        if (transferFromIndex > -1) {
            val startIndex = transferFromIndex + 8

            val startFromAddress = startIndex + 24
            val endFromAddress = startFromAddress + 40

            val startToAddress = endFromAddress + 24
            val endToAddress = startToAddress + 40

            val from = input.substring(startFromAddress, endFromAddress)
            val to = input.substring(startToAddress, endToAddress)
            val amount = input.substring(endToAddress, endToAddress + 64)

            return InputData(from = from, to = to, value = amount)
        }

        return null
    }

    class InputData(var from: String?, var to: String, var value: String)

}

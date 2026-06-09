package cash.p.terminal.modules.paycore

import android.content.Context
import android.content.SharedPreferences
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.IEncryptionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PayCoreSecureStorage(
    context: Context,
    private val encryptionManager: IEncryptionManager,
    private val userManager: UserManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("paycore_secure_prefs", Context.MODE_PRIVATE)

    enum class VerificationStatus { NOT_VERIFIED, VERIFIED, CANNOT_VERIFY }

    fun getPhone(): String? = readEncrypted(keyForLevel("phone"))

    fun setPhone(phone: String) {
        writeEncryptedSync(keyForLevel("phone"), phone)
    }

    fun getVerificationStatus(): VerificationStatus {
        val raw = readEncrypted(keyForLevel("verification_status"))
        return raw?.let { tryOrNull { VerificationStatus.valueOf(it) } } ?: VerificationStatus.NOT_VERIFIED
    }

    fun setVerificationStatus(status: VerificationStatus) {
        writeEncryptedSync(keyForLevel("verification_status"), status.name)
    }

    fun getLinkedWallet(): PayCoreLinkedWallet? {
        val raw = readEncrypted(keyForLevel("linked_wallet")) ?: return null
        return tryOrNull { Json.decodeFromString<PayCoreLinkedWallet>(raw) }
    }

    fun setLinkedWallet(wallet: PayCoreLinkedWallet) {
        writeEncryptedSync(keyForLevel("linked_wallet"), Json.encodeToString(wallet))
    }

    private fun readEncrypted(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return tryOrNull { encryptionManager.decrypt(stored) }
    }

    // Synchronous write — verification flow checkpoints (phone, linked wallet, verification
    // status) must survive process death between user action and the next read (which may
    // happen after WebView returns or app restarts).
    private fun writeEncryptedSync(key: String, value: String) {
        prefs.edit().putString(key, encryptionManager.encrypt(value)).commit()
    }

    private fun keyForLevel(key: String): String = "${key}_${userManager.getUserLevel()}"
}

@Serializable
data class PayCoreLinkedWallet(
    val accountId: String,
    val address: String,
    val networkType: PayCoreTicker,
)

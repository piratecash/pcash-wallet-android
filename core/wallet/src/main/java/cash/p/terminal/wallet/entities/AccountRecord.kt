package cash.p.terminal.wallet.entities

import android.os.Parcelable
import androidx.room.Entity
import kotlinx.parcelize.Parcelize

@Parcelize
class SecretString(val value: String) : Parcelable

@JvmInline
value class EncryptedString(val value: String)

class SecretList(val list: List<String>)

@Entity(primaryKeys = ["id"])
data class AccountRecord(
    val id: String,
    val name: String,
    val type: String,
    val origin: String,
    val isBackedUp: Boolean,
    val isFileBackedUp: Boolean,
    val words: SecretList?,
    val passphrase: SecretString?,
    val key: SecretString?,
    val level: Int
) {

    var deleted = false

    override fun equals(other: Any?): Boolean {
        if (other is AccountRecord) {
            return id == other.id
        }

        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}

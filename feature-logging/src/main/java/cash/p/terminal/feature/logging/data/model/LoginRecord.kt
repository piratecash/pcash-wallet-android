package cash.p.terminal.feature.logging.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.horizontalsystems.core.ILoginRecord

@Entity(
    tableName = "LoginRecord",
    indices = [
        Index("accountId"),
        Index("timestamp"),
        Index("userLevel")
    ]
)
internal data class LoginRecord(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0,
    override val timestamp: Long,
    override val isSuccessful: Boolean,
    override val userLevel: Int,
    override val accountId: String,
    override val photoPath: String? = null
) : ILoginRecord

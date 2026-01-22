package cash.p.terminal.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BackupKeyInput(val accountId: String) : Parcelable

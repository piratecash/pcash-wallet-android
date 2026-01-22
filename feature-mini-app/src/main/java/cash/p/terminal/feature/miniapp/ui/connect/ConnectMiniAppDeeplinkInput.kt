package cash.p.terminal.feature.miniapp.ui.connect

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConnectMiniAppDeeplinkInput(
    val jwt: String,
    val endpoint: String
) : Parcelable

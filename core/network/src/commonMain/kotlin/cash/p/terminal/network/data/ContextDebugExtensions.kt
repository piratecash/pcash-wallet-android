package cash.p.terminal.network.data

import android.content.Context
import android.content.pm.ApplicationInfo

internal fun Context.isAppDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

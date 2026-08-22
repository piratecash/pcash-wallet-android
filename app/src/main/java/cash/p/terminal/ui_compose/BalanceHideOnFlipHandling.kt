package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import cash.p.terminal.core.managers.BalanceHideOnFlipManager
import org.koin.compose.koinInject

@Composable
internal fun BalanceHideOnFlipHandling(allowed: Boolean = true) {
    val manager = koinInject<BalanceHideOnFlipManager>()
    val owner = remember { Any() }

    DisposableEffect(manager, allowed) {
        manager.setHandlingAllowed(owner, allowed)
        onDispose { manager.setHandlingAllowed(owner, false) }
    }
}

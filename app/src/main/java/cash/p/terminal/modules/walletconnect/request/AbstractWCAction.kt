package cash.p.terminal.modules.walletconnect.request

import cash.p.terminal.core.ServiceState
import cash.p.terminal.strings.helpers.TranslatableString
import kotlinx.coroutines.CoroutineScope

abstract class AbstractWCAction : ServiceState<WCActionState>() {
    abstract fun start(coroutineScope: CoroutineScope)
    abstract suspend fun performAction(): String

    abstract fun getTitle(): TranslatableString
    abstract fun getApproveButtonTitle(): TranslatableString
}

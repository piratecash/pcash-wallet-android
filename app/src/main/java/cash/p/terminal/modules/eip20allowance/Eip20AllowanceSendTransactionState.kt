package cash.p.terminal.modules.eip20allowance

import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal fun CoroutineScope.collectSendTransactionServiceState(
    sendTransactionServiceFlow: StateFlow<ISendTransactionService<*>?>,
    onStateChange: (SendTransactionServiceState) -> Unit,
) = launch {
    sendTransactionServiceFlow.filterNotNull().collect { service ->
        service.stateFlow.collect(onStateChange)
    }
}

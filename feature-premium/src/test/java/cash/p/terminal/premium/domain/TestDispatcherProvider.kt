package cash.p.terminal.premium.domain

import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher,
    override val applicationScope: CoroutineScope,
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
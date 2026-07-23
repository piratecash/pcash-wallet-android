package cash.p.terminal.core

import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher,
    override val applicationScope: CoroutineScope,
    // A distinct io dispatcher lets tests exercise real main<->io dispatch boundaries
    // (cancellation checkpoints) that a single shared dispatcher optimizes away.
    override val io: CoroutineDispatcher = dispatcher,
) : DispatcherProvider {
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
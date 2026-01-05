package io.horizontalsystems.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher

    /**
     * Application-scoped CoroutineScope for fire-and-forget operations
     * that must complete even if the calling ViewModel/screen is destroyed.
     */
    val applicationScope: CoroutineScope
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

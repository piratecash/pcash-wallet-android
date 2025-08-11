package cash.p.terminal.core.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.manager.IConnectivityManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class ConnectivityManager(
    backgroundManager: BackgroundManager,
    private val localStorage: ILocalStorage
) : IConnectivityManager {

    private val systemConnectivityManager: ConnectivityManager by lazy {
        App.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _networkAvailabilityFlow = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val networkAvailabilityFlow = _networkAvailabilityFlow.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow()

    override val torEnabled: Boolean
        get() = localStorage.torEnabled

    private var callback: ConnectionStatusCallback? = null
    private val isCallbackRegistered = AtomicBoolean(false)
    private val connectionStateMutex = Mutex()
    private var hasValidInternet = false
    private var hasConnection = false

    init {
        scope.launch {
            setInitialValues()
        }

        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                when (state) {
                    BackgroundManagerState.EnterForeground -> {
                        willEnterForeground()
                    }
                    BackgroundManagerState.EnterBackground -> {
                        didEnterBackground()
                    }
                    BackgroundManagerState.AllActivitiesDestroyed -> {
                        cleanup()
                    }
                    BackgroundManagerState.Unknown -> {
                        // do nothing
                    }
                }
            }
        }
    }

    private suspend fun willEnterForeground() {
        setInitialValues()

        if (callback == null) {
            callback = ConnectionStatusCallback()
        }

        unregisterCallbackSafely()
        registerCallback()
    }

    private fun didEnterBackground() {
        unregisterCallbackSafely()
    }

    private fun cleanup() {
        unregisterCallbackSafely()
        callback = null
    }

    private fun registerCallback() {
        callback?.let { cb ->
            // Set flag first to prevent repeated attempts
            if (isCallbackRegistered.compareAndSet(false, true)) {
                runCatching {
                    systemConnectivityManager.registerNetworkCallback(
                        NetworkRequest.Builder().build(),
                        cb
                    )
                }.onFailure {
                    isCallbackRegistered.set(false)
                }
            }
        }
    }

    private fun unregisterCallbackSafely() {
        if (isCallbackRegistered.getAndSet(false) && callback != null) {
            runCatching {
                systemConnectivityManager.unregisterNetworkCallback(callback!!)
            }
        }
    }

    private suspend fun setInitialValues() {
        connectionStateMutex.withLock {
            hasConnection = false
            hasValidInternet = false
        }

        val initialStatus = getInitialConnectionStatus()
        _isConnected.value = initialStatus
        _networkAvailabilityFlow.tryEmit(initialStatus)
    }

    private suspend fun getInitialConnectionStatus(): Boolean {
        val network = systemConnectivityManager.activeNetwork ?: return false

        return connectionStateMutex.withLock {
            hasConnection = true
            hasValidInternet = systemConnectivityManager.getNetworkCapabilities(network)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false

            hasValidInternet
        }
    }

    inner class ConnectionStatusCallback : ConnectivityManager.NetworkCallback() {

        private val activeNetworks = mutableSetOf<Network>()

        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            scope.launch {
                connectionStateMutex.withLock {
                    activeNetworks.add(network)
                    hasConnection = activeNetworks.isNotEmpty()
                }

                updatedConnectionState()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)

            scope.launch {
                connectionStateMutex.withLock {
                    activeNetworks.remove(network)
                    hasConnection = activeNetworks.isNotEmpty()
                }

                updatedConnectionState()
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            scope.launch {
                connectionStateMutex.withLock {
                    hasValidInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }

                updatedConnectionState()
            }
        }

        override fun onUnavailable() {
            super.onUnavailable()

            scope.launch {
                connectionStateMutex.withLock {
                    hasConnection = false
                }

                updatedConnectionState()
            }
        }
    }

    private suspend fun updatedConnectionState() {
        val oldValue = _isConnected.value
        val newValue = connectionStateMutex.withLock {
            hasConnection && hasValidInternet
        }

        if (oldValue != newValue) {
            _isConnected.value = newValue
            _networkAvailabilityFlow.tryEmit(newValue)
        }
    }
}
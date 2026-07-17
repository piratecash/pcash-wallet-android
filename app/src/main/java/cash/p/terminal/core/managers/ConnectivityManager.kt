package cash.p.terminal.core.managers

import android.net.ConnectivityManager as AndroidConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.manager.IConnectivityManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

// While foreground, re-derive connectivity from the system on this cadence so a missed
// callback event (or a failed registration) can't leave isConnected stale indefinitely.
private const val REVALIDATE_INTERVAL_MS = 15_000L
// One delayed retry after a failed callback registration.
private const val CALLBACK_RETRY_DELAY_MS = 2_000L

class ConnectivityManager(
    backgroundManager: BackgroundManager,
    private val localStorage: ILocalStorage,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val systemConnectivityManager: AndroidConnectivityManager,
    dispatcherProvider: DispatcherProvider,
) : IConnectivityManager {

    private val scope = CoroutineScope(dispatcherProvider.default)

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

    // Actor pattern: single channel ensures FIFO processing of network events
    private val eventChannel = Channel<NetworkEvent>(Channel.UNLIMITED)

    // Foreground safety-net timer (see REVALIDATE_INTERVAL_MS)
    private var revalidateJob: Job? = null

    // State managed only by the event processor (no mutex needed)
    private val activeNetworks = mutableSetOf<Network>()
    private val networkValidationMap = mutableMapOf<Network, Boolean>()

    private sealed class NetworkEvent {
        data class Available(val network: Network) : NetworkEvent()
        data class Lost(val network: Network) : NetworkEvent()
        data class CapabilitiesChanged(
            val network: Network,
            val capabilities: NetworkCapabilities
        ) : NetworkEvent()
        data object Unavailable : NetworkEvent()
        data class Initialize(
            val network: Network?,
            val hasValidInternet: Boolean,
            val forceEmit: Boolean
        ) : NetworkEvent()
    }

    init {
        // Single coroutine processes all events in order - no races possible
        scope.launch {
            eventChannel.receiveAsFlow().collect { event ->
                processEvent(event)
            }
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

        refreshFromSystem(forceEmit = true)
    }

    private fun processEvent(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.Available -> {
                activeNetworks.add(event.network)
                // Initialize validation state as false until capabilities callback arrives
                networkValidationMap[event.network] = false
            }
            is NetworkEvent.Lost -> {
                activeNetworks.remove(event.network)
                networkValidationMap.remove(event.network)
            }
            is NetworkEvent.CapabilitiesChanged -> {
                // Only process if network is still active (prevents resurrection after onLost)
                if (activeNetworks.contains(event.network)) {
                    val isValidated = event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    networkValidationMap[event.network] = isValidated
                }
            }
            is NetworkEvent.Unavailable -> {
                activeNetworks.clear()
                networkValidationMap.clear()
            }
            is NetworkEvent.Initialize -> {
                activeNetworks.clear()
                networkValidationMap.clear()

                event.network?.let { network ->
                    activeNetworks.add(network)
                    networkValidationMap[network] = event.hasValidInternet
                }
            }
        }
        updateConnectionState(forceEmit = (event as? NetworkEvent.Initialize)?.forceEmit == true)
    }

    private fun updateConnectionState(forceEmit: Boolean = false) {
        val hasConnection = activeNetworks.isNotEmpty()
        val hasValidInternet = networkValidationMap.values.any { it }
        val newValue = hasConnection && hasValidInternet

        val oldValue = _isConnected.value
        _isConnected.value = newValue
        if (forceEmit || oldValue != newValue) {
            _networkAvailabilityFlow.tryEmit(newValue)
        }
    }

    private fun willEnterForeground() {
        if (callback == null) {
            callback = ConnectionStatusCallback()
        }

        unregisterCallbackSafely()
        refreshFromSystem(forceEmit = true)
        registerCallback()
        startRevalidateTimer()
    }

    private fun didEnterBackground() {
        stopRevalidateTimer()
        if (backgroundKeepAliveManager.keepAliveBlockchains.value.isEmpty()) {
            unregisterCallbackSafely()
        }
    }

    private fun cleanup() {
        stopRevalidateTimer()
        unregisterCallbackSafely()
        callback = null
    }

    private fun registerCallback(isRetry: Boolean = false) {
        callback?.let { cb ->
            // Set flag first to prevent repeated attempts
            if (isCallbackRegistered.compareAndSet(false, true)) {
                runCatching {
                    systemConnectivityManager.registerNetworkCallback(
                        NetworkRequest.Builder().build(),
                        cb
                    )
                }.onFailure { error ->
                    isCallbackRegistered.set(false)
                    Timber.e(error, "Network callback registration failed; relying on periodic refresh")
                    // Without a live callback the state would freeze — read the system now so it isn't
                    // left stale, then retry registration once shortly after.
                    refreshFromSystem(forceEmit = false)
                    if (!isRetry) {
                        scope.launch {
                            delay(CALLBACK_RETRY_DELAY_MS)
                            registerCallback(isRetry = true)
                        }
                    }
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

    private fun startRevalidateTimer() {
        revalidateJob?.cancel()
        revalidateJob = scope.launch {
            while (isActive) {
                delay(REVALIDATE_INTERVAL_MS)
                refreshFromSystem(forceEmit = false)
            }
        }
    }

    private fun stopRevalidateTimer() {
        revalidateJob?.cancel()
        revalidateJob = null
    }

    private fun refreshFromSystem(forceEmit: Boolean) {
        val network = systemConnectivityManager.activeNetwork
        val hasValidInternet = network?.let { activeNetwork ->
            systemConnectivityManager.getNetworkCapabilities(activeNetwork)?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
        } ?: false

        eventChannel.trySend(
            NetworkEvent.Initialize(
                network = network,
                hasValidInternet = hasValidInternet,
                forceEmit = forceEmit
            )
        )
    }

    inner class ConnectionStatusCallback : AndroidConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            eventChannel.trySend(NetworkEvent.Available(network))
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            eventChannel.trySend(NetworkEvent.Lost(network))
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            eventChannel.trySend(NetworkEvent.CapabilitiesChanged(network, networkCapabilities))
        }

        override fun onUnavailable() {
            super.onUnavailable()
            eventChannel.trySend(NetworkEvent.Unavailable)
        }
    }
}

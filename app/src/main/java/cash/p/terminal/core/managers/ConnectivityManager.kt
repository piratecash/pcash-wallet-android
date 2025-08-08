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
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class ConnectivityManager(
    backgroundManager: BackgroundManager,
    private val localStorage: ILocalStorage
) : IConnectivityManager {

    private val connectivityManager: ConnectivityManager by lazy {
        App.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _networkAvailabilityFlow =
        MutableSharedFlow<Boolean>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    val networkAvailabilityFlow = _networkAvailabilityFlow.asSharedFlow()

    private val _isConnected = MutableStateFlow(getInitialConnectionStatus())
    override val isConnected = _isConnected.asStateFlow()

    override val torEnabled: Boolean
        get() = localStorage.torEnabled

    val networkAvailabilitySignal = PublishSubject.create<Unit>()

    private var callback = ConnectionStatusCallback()
    private var hasValidInternet = false
    private var hasConnection = false

    init {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                when (state) {
                    BackgroundManagerState.EnterForeground -> {
                        willEnterForeground()
                    }

                    BackgroundManagerState.EnterBackground -> {
                        didEnterBackground()
                    }

                    BackgroundManagerState.Unknown,
                    BackgroundManagerState.AllActivitiesDestroyed -> {
                        //do nothing
                    }
                }
            }
        }
    }

    private fun willEnterForeground() {
        setInitialValues()
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            //was not registered, or already unregistered
        }
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
    }

    private fun didEnterBackground() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            //already unregistered
        }
    }

    private fun setInitialValues() {
        hasConnection = false
        hasValidInternet = false
        val initialStatus = getInitialConnectionStatus()
        _isConnected.value = initialStatus
        networkAvailabilitySignal.onNext(Unit)
        _networkAvailabilityFlow.tryEmit(initialStatus)
    }

    private fun getInitialConnectionStatus(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false

        hasConnection = true
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        hasValidInternet = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        } ?: false

        return hasValidInternet
    }

    inner class ConnectionStatusCallback : ConnectivityManager.NetworkCallback() {

        private val activeNetworks: MutableList<Network> = mutableListOf()

        override fun onLost(network: Network) {
            super.onLost(network)
            activeNetworks.removeAll { activeNetwork -> activeNetwork == network }
            hasConnection = activeNetworks.isNotEmpty()
            updatedConnectionState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            hasValidInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            updatedConnectionState()
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (activeNetworks.none { activeNetwork -> activeNetwork == network }) {
                activeNetworks.add(network)
            }
            hasConnection = activeNetworks.isNotEmpty()
            updatedConnectionState()
        }
    }

    private fun updatedConnectionState() {
        val oldValue = _isConnected.value
        val newValue = hasConnection && hasValidInternet
        if (oldValue != newValue) {
            _isConnected.value = newValue
            networkAvailabilitySignal.onNext(Unit)
            _networkAvailabilityFlow.tryEmit(newValue)
        }
    }

}
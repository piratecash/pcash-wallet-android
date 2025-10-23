package cash.p.terminal.modules.main

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.DAppRequestEntityWrapper
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.modules.lockscreen.LockScreenActivity
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.IKeyStoreManager
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.security.KeyStoreValidationError
import io.horizontalsystems.tonkit.models.SignTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class MainActivityViewModel(
    private val userManager: DefaultUserManager,
    private val accountManager: IAccountManager,
    private val systemInfoManager: ISystemInfoManager,
    private val localStorage: ILocalStorage,
    private val checkPremiumUseCase: CheckPremiumUseCase
) : ViewModel() {

    private val pinComponent: IPinComponent = App.pinComponent
    private val keyStoreManager: IKeyStoreManager = App.keyStoreManager
    private val tonConnectManager: TonConnectManager = App.tonConnectManager

    val navigateToMainLiveData = MutableLiveData(false)
    val wcEvent = MutableLiveData<Wallet.Model?>()

    private val _tcSendRequest = MutableSharedFlow<SignTransaction?>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val tcSendRequest: SharedFlow<SignTransaction?> = _tcSendRequest.asSharedFlow()


    val tcDappRequest = MutableLiveData<DAppRequestEntityWrapper?>()
    val intentLiveData = MutableLiveData<Intent?>()

    private val backgroundManager: BackgroundManager by inject(BackgroundManager::class.java)
    private var lockScreenJob: Job? = null

    init {
        viewModelScope.launch {
            userManager.currentUserLevelFlow.collect {
                navigateToMainLiveData.postValue(true)
                updatePremiumStatus()
            }
        }
        viewModelScope.launch {
            WCDelegate.walletEvents.collect {
                wcEvent.postValue(it)
            }
        }
        viewModelScope.launch {
            tonConnectManager.sendRequestFlow.collect {
                _tcSendRequest.emit(it)
            }
        }
        viewModelScope.launch {
            tonConnectManager.dappRequestFlow.collect {
                tcDappRequest.postValue(it)
            }
        }
    }

    private fun updatePremiumStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            checkPremiumUseCase.update()
        }
    }

    fun startLockScreenMonitoring() {
        lockScreenJob?.cancel()
        lockScreenJob = viewModelScope.launch {
            pinComponent.isLocked.collectLatest { isLocked ->
                if (isLocked) {
                    backgroundManager.currentActivity?.let {
                        LockScreenActivity.start(it)
                    }
                }
            }
        }
    }

    fun stopLockScreenMonitoring() {
        lockScreenJob?.cancel()
    }

    fun setIntent(intent: Intent) {
        intentLiveData.postValue(intent)
    }

    fun intentHandled() {
        intentLiveData.postValue(null)
    }

    fun onTcDappRequestHandled() {
        tcDappRequest.postValue(null)
    }

    fun onWcEventHandled() {
        wcEvent.postValue(null)
    }

    fun onTcSendRequestHandled() {
        _tcSendRequest.tryEmit(null)
    }

    fun validate() {
        if (systemInfoManager.isSystemLockOff) {
            throw MainScreenValidationError.NoSystemLock()
        }

        try {
            keyStoreManager.validateKeyStore()
        } catch (e: KeyStoreValidationError.UserNotAuthenticated) {
            throw MainScreenValidationError.UserAuthentication()
        } catch (e: KeyStoreValidationError.KeyIsInvalid) {
            throw MainScreenValidationError.KeyInvalidated()
        } catch (e: RuntimeException) {
            throw MainScreenValidationError.KeystoreRuntimeException()
        }

        if (accountManager.isAccountsEmpty && !localStorage.mainShowedOnce) {
            throw MainScreenValidationError.Welcome()
        }
    }

    fun onNavigatedToMain() {
        navigateToMainLiveData.postValue(false)
    }

    fun selectBalanceTabOnNextLaunch() {
        localStorage.selectBalanceTabOnNextLaunch = true
    }
}

sealed class MainScreenValidationError : Exception() {
    class Welcome : MainScreenValidationError()
    class Unlock : MainScreenValidationError()
    class NoSystemLock : MainScreenValidationError()
    class KeyInvalidated : MainScreenValidationError()
    class UserAuthentication : MainScreenValidationError()
    class KeystoreRuntimeException : MainScreenValidationError()
}

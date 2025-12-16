package cash.p.terminal.modules.pin

import cash.p.terminal.core.App
import io.horizontalsystems.core.DispatcherProvider
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.domain.usecase.ResetUseCase
import cash.p.terminal.modules.pin.core.LockManager
import cash.p.terminal.modules.pin.core.PinDbStorage
import cash.p.terminal.modules.pin.core.PinLevels
import cash.p.terminal.modules.pin.core.PinManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.IPinSettingsStorage
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class PinComponent(
    private val pinSettingsStorage: IPinSettingsStorage,
    private val userManager: DefaultUserManager,
    private val pinDbStorage: PinDbStorage,
    private val backgroundManager: BackgroundManager,
    private val resetUseCase: ResetUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(Executors.newFixedThreadPool(5).asCoroutineDispatcher())
) : IPinComponent {

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
                    BackgroundManagerState.AllActivitiesDestroyed -> {
                       lock()
                    }
                    BackgroundManagerState.Unknown -> {
                        //do nothing
                    }
                }
            }
        }
    }

    private val pinManager: PinManager by lazy {
        PinManager(pinDbStorage)
    }

    private val appLockManager: LockManager by lazy {
        LockManager(pinManager, App.localStorage)
    }

    override val pinSetFlowable: Flowable<Unit>
        get() = pinManager.pinSetSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val isLocked: StateFlow<Boolean> = appLockManager.isLocked
        .map { isLocked -> isLocked && isPinSet }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    override var isBiometricAuthEnabled: Boolean
        get() = pinSettingsStorage.biometricAuthEnabled
        set(value) {
            pinSettingsStorage.biometricAuthEnabled = value
        }

    override val isPinSet: Boolean
        get() = pinManager.isPinSet

    override fun getDuressLevel(): Int {
        var level = userManager.getUserLevel() + 1
        // Skip reserved level for Secure Reset PIN
        if (level == PinLevels.SECURE_RESET) {
            level++
        }
        return level
    }

    override fun isUnique(pin: String, forDuress: Boolean): Boolean {
        val level = if (forDuress) {
            getDuressLevel()
        } else {
            userManager.getUserLevel()
        }
        return pinManager.isUnique(pin, level)
    }

    override fun setPin(pin: String) {
        if (appLockManager.isLocked.value) {
            appLockManager.onUnlock()
        }

        pinManager.store(pin, userManager.getUserLevel())
    }

    override fun setDuressPin(pin: String) {
        pinManager.store(pin, getDuressLevel())
    }

    override fun validateCurrentLevel(pin: String): Boolean {
        val pinLevel = pinManager.getPinLevel(pin) ?: return false
        return pinLevel == userManager.getUserLevel()
    }

    override fun isDuressPinSet(): Boolean {
        return pinManager.isPinSetForLevel(getDuressLevel())
    }

    override fun disablePin() {
        pinManager.disablePin(userManager.getUserLevel())
        userManager.disallowAccountsForDuress()
    }

    override fun disableDuressPin() {
        pinManager.disableDuressPin(getDuressLevel())
        userManager.disallowAccountsForDuress()
    }

    override suspend fun unlock(pin: String): Boolean = withContext(dispatcherProvider.io) {
        var pinLevel = pinManager.getPinLevel(pin) ?: return@withContext false

        if (pinLevel == PinLevels.SECURE_RESET) {
            disableSecureResetPin()
            resetUseCase()
            pinManager.store(pin, 0)
            pinLevel = 0
        }

        appLockManager.onUnlock()
        userManager.setUserLevel(pinLevel)

        true
    }

    override fun initDefaultPinLevel() {
        userManager.setUserLevel(pinManager.getPinLevelLast())
    }

    override fun onBiometricUnlock() {
        appLockManager.onUnlock()
        userManager.setUserLevel(pinManager.getPinLevelLast())
    }

    override fun lock() {
        appLockManager.lock()
    }

    override fun updateLastExitDateBeforeRestart() {
        appLockManager.updateLastExitDate()
    }

    override fun willEnterForeground() {
        appLockManager.willEnterForeground()
    }

    override fun didEnterBackground() {
        appLockManager.didEnterBackground()
    }

    override fun keepUnlocked() {
        appLockManager.keepUnlocked()
    }

    override fun getPinLevel(pin: String): Int? {
        return pinManager.getPinLevel(pin)
    }

    override fun setHiddenWalletPin(pin: String): Int {
        val nextLevel = pinManager.getNextHiddenWalletLevel()
        pinManager.store(pin, nextLevel)
        return nextLevel
    }

    override fun setSecureResetPin(pin: String) {
        pinManager.store(pin, PinLevels.SECURE_RESET)
    }

    override fun isSecureResetPinSet(): Boolean {
        return pinManager.isPinSetForLevel(PinLevels.SECURE_RESET)
    }

    override fun disableSecureResetPin() {
        pinManager.disablePin(PinLevels.SECURE_RESET)
    }
}

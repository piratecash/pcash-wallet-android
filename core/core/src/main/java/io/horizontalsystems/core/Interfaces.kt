package io.horizontalsystems.core

import io.reactivex.Flowable
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import javax.crypto.SecretKey

interface ICoreApp {
    var backgroundManager: BackgroundManager
    var encryptionManager: IEncryptionManager
    var systemInfoManager: ISystemInfoManager
    var keyStoreManager: IKeyStoreManager
    var keyProvider: IKeyProvider
    var pinComponent: IPinComponent
    var pinSettingsStorage: IPinSettingsStorage
    var lockoutStorage: ILockoutStorage
    var thirdKeyboardStorage: IThirdKeyboard
    var instance: CoreApp
}

interface IEncryptionManager {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}

interface ISystemInfoManager {
    val appVersion: String
    val isDeviceSecure: Boolean
    val isSystemLockOff: Boolean
    val biometricAuthSupported: Boolean
    val deviceModel: String
    val osVersion: String
}

interface IPinComponent {
    var isBiometricAuthEnabled: Boolean
    val isPinSet: Boolean
    val isLocked: StateFlow<Boolean>
    val pinSetFlowable: Flowable<Unit>

    fun willEnterForeground()
    fun didEnterBackground()
    fun setPin(pin: String)
    fun setDuressPin(pin: String)
    fun disablePin()
    fun disableDuressPin()
    fun isDuressPinSet(): Boolean
    fun unlock(pin: String): Boolean
    fun validateCurrentLevel(pin: String): Boolean
    fun onBiometricUnlock()
    fun initDefaultPinLevel()
    fun lock()
    fun updateLastExitDateBeforeRestart()
    fun isUnique(pin: String, forDuress: Boolean): Boolean
    fun keepUnlocked()
}

interface ILockoutStorage {
    var failedAttempts: Int?
    var lockoutUptime: Long?
}

interface IPinSettingsStorage {
    var biometricAuthEnabled: Boolean
    var pin: String?

    var isSystemPinRequired: Boolean
}

interface IThirdKeyboard {
    var isThirdPartyKeyboardAllowed: Boolean
}

interface IKeyStoreManager {
    fun validateKeyStore()
    fun removeKey()
    fun resetApp(reason: String)
}

interface IKeyStoreCleaner {
    var encryptedSampleText: String?
    fun cleanApp()
}

interface IKeyProvider {
    fun getKey(): SecretKey
}

interface ICurrentDateProvider {
    val currentDate: Date
}

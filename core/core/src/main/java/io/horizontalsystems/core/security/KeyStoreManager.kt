package io.horizontalsystems.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import io.horizontalsystems.core.IKeyProvider
import io.horizontalsystems.core.IKeyStoreCleaner
import io.horizontalsystems.core.IKeyStoreManager
import io.horizontalsystems.core.IPinSettingsStorage
import org.koin.java.KoinJavaComponent.inject
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreManager(
    private val keyAlias: String,
    private val keyStoreCleaner: IKeyStoreCleaner,
    private val logger: Logger,
) : IKeyStoreManager, IKeyProvider {

    private val ANDROID_KEY_STORE = "AndroidKeyStore"
    private val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private val AUTH_DURATION_SEC = 86400 // 24 hours in seconds (24x60x60)

    private val localStoreManager: IPinSettingsStorage by inject(IPinSettingsStorage::class.java)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE)

    interface Logger {
        fun warning(message: String, e: Throwable)
        fun info(message: String)
    }

    init {
        keyStore.load(null)
    }

    override fun getKey(): SecretKey {
        try {
            keyStore.getKey(keyAlias, null)?.let {
                return it as SecretKey
            }
        } catch (ex: UnrecoverableKeyException) {
            //couldn't get key due to exception
        }
        return createKey()
    }

    override fun removeKey() {
        try {
            keyStore.deleteEntry(keyAlias)
        } catch (ex: KeyStoreException) {
            logger.warning("remove key failed", ex)
        }
    }

    override fun resetApp(reason: String) {
        logger.info("resetting app, reason: $reason")

        keyStoreCleaner.cleanApp()
    }

    override fun validateKeyStore() {
        try {
            validateKey()
        } catch (ex: UserNotAuthenticatedException) {
            logger.warning("invalid key", ex)
            throw KeyStoreValidationError.UserNotAuthenticated()
        } catch (ex: KeyPermanentlyInvalidatedException) {
            logger.warning("invalid key", ex)
            throw KeyStoreValidationError.KeyIsInvalid()
        } catch (ex: UnrecoverableKeyException) {
            logger.warning("invalid key", ex)
            throw KeyStoreValidationError.KeyIsInvalid()
        } catch (ex: InvalidKeyException) {
            logger.warning("invalid key", ex)
            throw KeyStoreValidationError.KeyIsInvalid()
        } catch (ex: BadPaddingException) {
            logger.warning("invalid key", ex)
            throw KeyStoreValidationError.KeyIsInvalid()
        }
    }

    @Synchronized
    private fun createKey(): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)

        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setUserAuthenticationRequired(localStoreManager.isSystemPinRequired)
            .setRandomizedEncryptionRequired(false)
            .setEncryptionPaddings(PADDING)

        if (localStoreManager.isSystemPinRequired) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    AUTH_DURATION_SEC,
                    KeyProperties.AUTH_DEVICE_CREDENTIAL
                            or KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_DURATION_SEC)
            }
        }

        keyGenerator.init(builder.build())

        return keyGenerator.generateKey()
    }

    private fun validateKey() {
        keyStoreCleaner.encryptedSampleText?.let { encryptedText ->
            val key = keyStore.getKey(keyAlias, null) ?: throw InvalidKeyException()
            CipherWrapper().decrypt(encryptedText, key)
        } ?: run {
            val text = CipherWrapper().encrypt("abc", getKey())
            keyStoreCleaner.encryptedSampleText = text
        }
    }

}

sealed class KeyStoreValidationError : Error() {
    class UserNotAuthenticated : KeyStoreValidationError()
    class KeyIsInvalid : KeyStoreValidationError()
}

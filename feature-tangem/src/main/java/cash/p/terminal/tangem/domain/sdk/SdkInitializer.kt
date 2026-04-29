package cash.p.terminal.tangem.domain.sdk

import androidx.fragment.app.FragmentActivity
import com.tangem.TangemSdk
import com.tangem.common.CardFilter
import com.tangem.common.authentication.AuthenticationManager
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.Config
import com.tangem.common.services.secure.SecureStorage
import com.tangem.crypto.bip39.Wordlist
import com.tangem.sdk.DefaultSessionViewDelegate
import com.tangem.sdk.extensions.getWordlist
import com.tangem.sdk.extensions.initAuthenticationManager
import com.tangem.sdk.extensions.initKeystoreManager
import com.tangem.sdk.extensions.initNfcManager
import com.tangem.sdk.nfc.AndroidNfcAvailabilityProvider
import com.tangem.sdk.nfc.NfcManager
import com.tangem.sdk.storage.create

interface SdkInitializer {

    fun create(activity: FragmentActivity): Components

    data class Components(
        val nfcManager: NfcManager,
        val authenticationManager: AuthenticationManager,
        val sdk: TangemSdk,
    )
}

object DefaultSdkInitializer : SdkInitializer {

    override fun create(activity: FragmentActivity): SdkInitializer.Components {
        val secureStorage = SecureStorage.create(activity)
        val nfcManager = TangemSdk.initNfcManager(activity)
        val authenticationManager = TangemSdk.initAuthenticationManager(activity)
        val keystoreManager = TangemSdk.initKeystoreManager(authenticationManager, secureStorage)

        val viewDelegate = DefaultSessionViewDelegate(nfcManager, activity)
        viewDelegate.sdkConfig = config

        val androidNfcAvailabilityProvider = AndroidNfcAvailabilityProvider(activity)
        val sdk = TangemSdk(
            reader = nfcManager.reader,
            viewDelegate = viewDelegate,
            nfcAvailabilityProvider = androidNfcAvailabilityProvider,
            secureStorage = secureStorage,
            authenticationManager = authenticationManager,
            keystoreManager = keystoreManager,
            wordlist = Wordlist.getWordlist(activity),
            config = config,
        )

        return SdkInitializer.Components(
            nfcManager = nfcManager,
            authenticationManager = authenticationManager,
            sdk = sdk,
        )
    }

    private const val TANGEM_API_BASE_URL = "https://api.tangem.org/"

    private val config = Config(
        linkedTerminal = true,
        filter = CardFilter(
            allowedCardTypes = FirmwareVersion.FirmwareType.entries.toList(),
            maxFirmwareVersion = FirmwareVersion(major = 6, minor = 33),
            batchIdFilter = CardFilter.Companion.ItemFilter.Deny(
                items = setOf("0027", "0030", "0031", "0035"),
            ),
        ),
        tangemApiBaseUrl = TANGEM_API_BASE_URL,
    )
}

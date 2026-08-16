package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorMoneroAdmissionPolicyTest {
    @Test
    fun supportsStoredToken_safe5NativeMoneroOnly() {
        assertTrue(
            TrezorMoneroAdmissionPolicy.supportsStoredToken(
                TrezorModel.Safe5.ids.single(),
                BlockchainType.Monero,
                TokenType.Native,
            ),
        )
        assertFalse(
            TrezorMoneroAdmissionPolicy.supportsStoredToken(
                TrezorModel.Safe5.ids.single(),
                BlockchainType.Ethereum,
                TokenType.Native,
            ),
        )
        assertFalse(
            TrezorMoneroAdmissionPolicy.supportsStoredToken(
                TrezorModel.Safe3.ids.first(),
                BlockchainType.Monero,
                TokenType.Native,
            ),
        )
    }

    @Test
    fun live_allChecksPass_isSupported() {
        assertNull(TrezorMoneroAdmissionPolicy.liveFailure(features()))
    }

    @Test
    fun live_eachRequiredCheckFails_isUnsupported() {
        val unsupported = listOf(
            features(internalModel = TrezorModel.ModelT.ids.single()),
            features(initialized = false),
            features(supportsMonero = false),
            features(firmwareVersion = "2.4.2"),
            features(firmwareVersion = "2.4"),
        )

        unsupported.forEach { features ->
            assertNotNull(TrezorMoneroAdmissionPolicy.liveFailure(features))
        }
    }

    @Test
    fun live_newerStructuredFirmware_isSupported() {
        assertNull(
            TrezorMoneroAdmissionPolicy.liveFailure(
                features(firmwareVersion = "2.10.0"),
            ),
        )
    }

    private fun features(
        internalModel: String = TrezorModel.Safe5.ids.single(),
        firmwareVersion: String = "2.4.3",
        initialized: Boolean = true,
        supportsMonero: Boolean = true,
    ) = TrezorFeatures(
        deviceId = "device-id",
        model = "Safe 5",
        internalModel = internalModel,
        firmwareVersion = firmwareVersion,
        passphraseProtection = false,
        initialized = initialized,
        supportsTron = false,
        supportsMonero = supportsMonero,
    )
}

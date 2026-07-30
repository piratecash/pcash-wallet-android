package cash.p.terminal.core.managers

import cash.p.terminal.trezorkit.TrezorUsbInterfaceUnavailableException
import cash.p.terminal.trezorkit.TrezorUsbOpenFailedException
import cash.p.terminal.trezorkit.client.TrezorFeatures
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class MoneroTrezorReadinessTest {
    private val readiness = MoneroTrezorReadiness()

    @Test
    fun requireWallet_samePassphraseWallet_succeeds() {
        readiness.requireWallet("wallet-key", "wallet-key")
    }

    @Test
    fun requireWallet_differentPassphraseWallet_throwsWrongWallet() {
        val error = assertFailsWith<HardwareWalletOperationException> {
            readiness.requireWallet("wallet-key", "different-key")
        }

        assertEquals(HardwareWalletErrorCode.WrongWallet, error.error)
    }

    @Test
    fun requireSession_missingSessionId_throwsProtocolError() {
        val error = assertFailsWith<HardwareWalletOperationException> {
            readiness.requireSession(
                TrezorFeatures(
                    deviceId = "device-id",
                    model = "Safe 5",
                    internalModel = "T3T1",
                    firmwareVersion = "2.8.10",
                    passphraseProtection = false,
                ),
            )
        }

        assertEquals(HardwareWalletErrorCode.Protocol, error.error)
    }

    @Test
    fun hardwareFailure_usbOpenFailed_preservesTypedError() {
        val error = readiness.hardwareFailure(TrezorUsbOpenFailedException())

        assertEquals(HardwareWalletErrorCode.UsbOpenFailed, error.error)
    }

    @Test
    fun hardwareFailure_usbInterfaceUnavailable_preservesTypedError() {
        val error = readiness.hardwareFailure(TrezorUsbInterfaceUnavailableException())

        assertEquals(HardwareWalletErrorCode.UsbInterfaceUnavailable, error.error)
    }
}

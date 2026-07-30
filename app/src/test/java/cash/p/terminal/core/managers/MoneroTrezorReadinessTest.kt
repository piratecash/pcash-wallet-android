package cash.p.terminal.core.managers

import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezorkit.TrezorUsbInterfaceUnavailableException
import cash.p.terminal.trezorkit.TrezorUsbOpenFailedException
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class MoneroTrezorReadinessTest {
    private val identityValidator = mockk<TrezorAccountIdentityValidator>()
    private val readiness = MoneroTrezorReadiness(identityValidator)
    private val account = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    @Test
    fun requireWallet_samePassphraseWallet_succeeds() = runTest {
        coEvery { identityValidator.matchesWallet(account, "wallet-key") } returns true

        readiness.requireWallet(account, "wallet-key")
    }

    @Test
    fun requireWallet_differentPassphraseWallet_throwsWrongWallet() = runTest {
        coEvery { identityValidator.matchesWallet(account, "different-key") } returns false

        val error = assertFailsWith<HardwareWalletOperationException> {
            readiness.requireWallet(account, "different-key")
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

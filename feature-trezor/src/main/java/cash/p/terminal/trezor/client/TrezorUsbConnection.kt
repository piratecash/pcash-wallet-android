package cash.p.terminal.trezor.client

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.trezorkit.TrezorTransportException
import cash.p.terminal.trezorkit.transport.UsbTrezorTransport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Locates the plugged-in Trezor and secures USB permission for it. Stateless on purpose: [acquire]
 * returns the device for the caller to use within a single [UsbTrezorClientProvider] session, so
 * there is no shared device field that overlapping flows could clobber.
 */
internal class TrezorUsbConnection(private val context: Context) {

    val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Returns the connected Trezor with USB permission granted, or throws if none/denied. */
    suspend fun acquire(): UsbDevice {
        val trezorDevices = usbManager.deviceList.values.filter { it.vendorId == UsbTrezorTransport.VENDOR_ID }
        val device = trezorDevices.firstOrNull { it.productId == UsbTrezorTransport.PRODUCT_ID_FIRMWARE }
        if (device == null) {
            // A Trezor present only in bootloader mode is not set up yet - report it as such rather
            // than "not found".
            if (trezorDevices.any { it.productId == UsbTrezorTransport.PRODUCT_ID_BOOTLOADER }) {
                throw TrezorNotInitializedException(
                    "Trezor is in bootloader mode. Complete setup on the device."
                )
            }
            throw TrezorTransportException("No Trezor found. Connect it with a cable.")
        }

        if (!usbManager.hasPermission(device)) {
            // withTimeoutOrNull (not withTimeout): a timeout must surface as a transport error, not a
            // TimeoutCancellationException that looks like coroutine cancellation and can wedge the
            // runBlocking send path when the user never answers the permission dialog.
            val granted = withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { requestPermission(device) }
                ?: throw TrezorTransportException("USB permission request timed out")
            if (!granted) throw TrezorTransportException("USB access to Trezor was denied.")
        }
        return device
    }

    private suspend fun requestPermission(target: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    unregister(this)
                    if (continuation.isActive) {
                        continuation.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            continuation.invokeOnCancellation { unregister(receiver) }

            // Explicit + mutable: Android U+ rejects mutable PendingIntents wrapping implicit intents,
            // and UsbManager needs mutability to attach the granted device.
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(target, permissionIntent)
        }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered - safe to ignore.
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "cash.p.terminal.trezor.USB_PERMISSION"
        private const val PERMISSION_TIMEOUT_MS = 60_000L
    }
}

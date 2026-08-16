package cash.p.terminal.trezor.client

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import cash.p.terminal.trezorkit.transport.UsbPermissionRequester
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TrezorUsbPermissionRequester(
    private val context: Context,
    private val usbManager: UsbManager,
) : UsbPermissionRequester {
    override suspend fun requestPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val requestToken = UUID.randomUUID().toString()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val result = when (intent.action) {
                        ACTION_USB_PERMISSION -> {
                            if (
                                intent.getStringExtra(EXTRA_REQUEST_TOKEN) != requestToken ||
                                intent.usbDevice()?.deviceName != device.deviceName
                            ) {
                                return
                            }
                            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        }

                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            if (intent.usbDevice()?.deviceName != device.deviceName) return
                            false
                        }

                        else -> return
                    }
                    unregister(this)
                    if (continuation.isActive) continuation.resume(result)
                }
            }
            try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(ACTION_USB_PERMISSION).apply {
                        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                continuation.invokeOnCancellation { unregister(receiver) }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                usbManager.requestPermission(device, permissionIntent(requestToken))
            } catch (error: RuntimeException) {
                unregister(receiver)
                continuation.resumeWithException(error)
            }
        }

    private fun permissionIntent(requestToken: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestToken.hashCode(),
            Intent(ACTION_USB_PERMISSION)
                .setPackage(context.packageName)
                .putExtra(EXTRA_REQUEST_TOKEN, requestToken),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
        )

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The permission result and coroutine cancellation may race.
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? = getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private companion object {
        const val ACTION_USB_PERMISSION = "cash.p.terminal.trezor.USB_PERMISSION"
        const val EXTRA_REQUEST_TOKEN = "request_token"
    }
}

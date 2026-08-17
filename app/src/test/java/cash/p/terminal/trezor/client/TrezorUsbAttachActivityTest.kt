package cash.p.terminal.trezor.client

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TrezorUsbAttachActivityTest {

    @Test
    fun onCreate_usbAttachIntent_finishesWithoutLaunchingAnotherActivity() {
        val activity = Robolectric.buildActivity(
            TrezorUsbAttachActivity::class.java,
            Intent("android.hardware.usb.action.USB_DEVICE_ATTACHED"),
        ).setup().get()

        assertTrue(activity.isFinishing)
        assertTrue(shadowOf(activity).nextStartedActivity == null)
    }
}

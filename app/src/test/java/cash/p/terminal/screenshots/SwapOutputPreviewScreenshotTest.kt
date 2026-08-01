package cash.p.terminal.screenshots

import android.app.Application
import cash.p.terminal.modules.multiswap.SwapOutputInputExactInPreview
import cash.p.terminal.modules.multiswap.SwapOutputInputExactOutPreview
import cash.p.terminal.modules.multiswap.SwapOutputInputNoPriceImpactPreview
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local-only design check. Run with:
 * `./gradlew :app:recordRoborazziDebug -Pscreenshots --tests "*SwapOutputPreviewScreenshotTest"`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class SwapOutputPreviewScreenshotTest {

    @Test
    fun snapshot_exactIn_rendersOutputField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputExactInPreview.png",
        ) {
            SwapOutputInputExactInPreview()
        }
    }

    @Test
    fun snapshot_exactOut_rendersOutputField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputExactOutPreview.png",
        ) {
            SwapOutputInputExactOutPreview()
        }
    }

    @Test
    fun snapshot_noPriceImpact_rendersExpandedFiatField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputNoPriceImpactPreview.png",
        ) {
            SwapOutputInputNoPriceImpactPreview()
        }
    }
}

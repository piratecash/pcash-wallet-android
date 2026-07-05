package cash.p.terminal.screenshots

import android.app.Application
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders the `@Preview` composables of the token balance screen to PNGs on the JVM
 * (Robolectric, no emulator). Previews are discovered automatically via
 * ComposablePreviewScanner, so no per-preview boilerplate is needed.
 *
 * Record images: `./gradlew :app:recordRoborazziDebug --tests "*TokenBalancePreviewScreenshotTest"`
 * Output: `app/build/outputs/roborazzi/<previewMethodName>.png`
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class TokenBalancePreviewScreenshotTest(
    private val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("cash.p.terminal.modules.balance.token")
                .getPreviews()
                .filter { it.methodName.startsWith("TokenBalanceScreenContent") }
    }

    @Test
    fun snapshot() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/${preview.methodName}.png",
        ) {
            preview()
        }
    }
}

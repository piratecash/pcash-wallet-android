package cash.p.terminal.screenshots

import android.app.Application
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockScreen
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockUiState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Creation Block screen to a PNG on the JVM (Robolectric, no emulator) for local
 * design review against the ART-385 mockup.
 *
 * Record: `./gradlew :app:recordRoborazziDebug --tests "*CreationBlockPreviewScreenshotTest"`
 * Output: `app/build/outputs/roborazzi/CreationBlockScreen.png`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class CreationBlockPreviewScreenshotTest {

    @Test
    fun snapshot() {
        captureRoboImage(filePath = "build/outputs/roborazzi/CreationBlockScreen.png") {
            ComposeAppTheme {
                CreationBlockScreen(
                    uiState = CreationBlockUiState(
                        blockchainType = BlockchainType.Monero,
                        heightText = "2975499",
                        blockDateText = "9 августа 2024 г.",
                        changed = true,
                    ),
                    onHeightChange = {},
                    onDatePick = {},
                    onRescanConfirm = {},
                    onClose = {},
                    onRescanStart = {},
                )
            }
        }
    }
}

package cash.p.terminal.ui_compose

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.LayoutRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

val LocalConnectionPanelState = compositionLocalOf {
    mutableStateOf(true)
}

abstract class BaseComposeFragment(
    @LayoutRes layoutResId: Int = 0,
    private val screenshotEnabled: Boolean = true
) : Fragment(layoutResId) {

    open val showConnectionPanel: Boolean = true

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )

            setContent {
                ComposeAppTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        GetContent(findNavController())
                        if (showConnectionPanel && LocalConnectionPanelState.current.value) {
                            ConnectionStatusView(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    protected inline fun <reified T : Parcelable> withInput(
        navController: NavController,
        content: @Composable (T) -> Unit
    ) {
        val input = try {
            navController.requireInput<T>()
        } catch (e: NullPointerException) {
            navController.popBackStack()
            return
        }
        content(input)
    }

    @Composable
    abstract fun GetContent(navController: NavController)

    override fun onResume() {
        super.onResume()
        if (screenshotEnabled) {
            allowScreenshot()
        } else {
            disallowScreenshot()
        }
    }

    override fun onPause() {
        disallowScreenshot()
        super.onPause()
    }

    private fun allowScreenshot() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun disallowScreenshot() {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

}
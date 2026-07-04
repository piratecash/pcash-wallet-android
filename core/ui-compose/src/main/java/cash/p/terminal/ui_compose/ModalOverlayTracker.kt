package cash.p.terminal.ui_compose

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Process-wide registry telling the main screen whether a modal overlay (Material 3 bottom sheet)
 * is holding the foreground.
 *
 * A modal renders in its own window and steals focus from the host activity window — the same
 * signal the main screen uses to detect the recent-apps switcher and hide its content for privacy.
 * That focus loss is ambiguous on its own, so a modal reports here when it is up front:
 *
 * - It counts as up front from the moment it opens (before its own window gains focus) so the host
 *   never flashes its privacy overlay during the brief gap while the activity window is already
 *   unfocused but the modal window is not yet focused.
 * - It stops counting once its window loses focus *after* having held it — i.e. the task moved to
 *   the recent-apps switcher (or Home). Then [hasForegroundModal] flips to false and the host hides
 *   its content for the task snapshot, the case plain activity-focus loss cannot distinguish.
 *
 * Dismissing a modal briefly leaves no window focused (its window is torn down a frame or two before
 * the activity window regains focus). [DISMISS_GRACE_MS] keeps [hasForegroundModal] true across that
 * gap so the privacy overlay does not flash. The grace is tied to the modal leaving composition
 * (dismiss), not to focus loss, so it never delays hiding for the recent-apps case.
 */
object ModalOverlayTracker {

    private const val DISMISS_GRACE_MS = 200L

    private val foregroundModals = mutableStateListOf<Any>()
    private val dismissGrace = mutableStateListOf<Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val hasForegroundModal: Boolean
        get() = foregroundModals.isNotEmpty() || dismissGrace.isNotEmpty()

    /**
     * Call inside a modal's content (it composes in the modal's own window) to report whether that
     * window is up front.
     */
    @Composable
    fun TrackForeground() {
        val token = remember { Any() }
        val focused = LocalWindowInfo.current.isWindowFocused
        var hasBeenFocused by remember { mutableStateOf(false) }
        LaunchedEffect(focused) {
            if (focused) hasBeenFocused = true
        }

        // Up front while opening (not yet focused) or focused; only a focus loss after having been
        // focused (entering recents) drops it.
        val upFront = focused || !hasBeenFocused
        DisposableEffect(upFront) {
            if (upFront) foregroundModals.add(token)
            onDispose { foregroundModals.remove(token) }
        }
        DisposableEffect(Unit) {
            onDispose {
                val graceToken = Any()
                dismissGrace.add(graceToken)
                mainHandler.postDelayed({ dismissGrace.remove(graceToken) }, DISMISS_GRACE_MS)
            }
        }
    }
}

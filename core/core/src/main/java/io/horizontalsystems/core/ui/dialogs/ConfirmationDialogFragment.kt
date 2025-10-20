package io.horizontalsystems.core.ui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentManager
import cash.p.terminal.ui_compose.BaseComposableBottomSheetFragment
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class ConfirmationDialogFragment(
    private val listener: Listener,
    private val title: String,
    private val icon: Int?,
    private val warningTitle: String?,
    private val warningText: String?,
    private val actionButtonTitle: String?,
    private val transparentButtonTitle: String?,
) : BaseComposableBottomSheetFragment() {

    interface Listener {
        fun onActionButtonClick() {}
        fun onTransparentButtonClick() {}
        fun onCancelButtonClick() {}
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        listener.onCancelButtonClick()
    }

    override fun close() {
        super.close()
        listener.onCancelButtonClick()
    }

    override fun onCreateView(
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
                    ConfirmationDialog(
                        title = title,
                        icon = icon,
                        warningTitle = warningTitle,
                        warningText = warningText,
                        actionButtonTitle = actionButtonTitle,
                        transparentButtonTitle = transparentButtonTitle,
                        onCloseClick = listener::onCancelButtonClick,
                        onActionButtonClick = listener::onActionButtonClick,
                        onTransparentButtonClick = listener::onTransparentButtonClick
                    )
                }
            }
        }
    }

    companion object Companion {

        fun show(
            icon: Int? = null,
            title: String,
            warningTitle: String? = null,
            warningText: String?,
            actionButtonTitle: String? = "",
            transparentButtonTitle: String? = "",
            fragmentManager: FragmentManager,
            listener: Listener,
        ) {

            val fragment = ConfirmationDialogFragment(
                listener,
                title,
                icon,
                warningTitle,
                warningText,
                actionButtonTitle,
                transparentButtonTitle,
            )
            val transaction = fragmentManager.beginTransaction()

            transaction.add(fragment, "bottom_coin_settings_alert_dialog")
            transaction.commitAllowingStateLoss()
        }
    }
}
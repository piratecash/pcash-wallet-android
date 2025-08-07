package cash.p.terminal.ui_compose

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment

//  Fragment

fun Fragment.findNavController(): NavController {
    return NavHostFragment.findNavController(this)
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T : Parcelable> Bundle.getInputX(): T? {
    return parcelable("input")
}

inline fun <reified T : Parcelable> NavController.getInput(): T? {
    return currentBackStackEntry?.arguments?.getInputX()
}

inline fun <reified T: Parcelable> NavController.requireInput() : T {
    return getInput()!!
}

@Composable
fun Modifier.blockClicksBehind() = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() }
) { /* Do nothing */ }

fun NavController.slideFromBottom(@IdRes resId: Int, input: Parcelable? = null) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_bottom)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_bottom)
        .build()

    val args = input?.let {
        bundleOf("input" to it)
    }
    navigate(resId, args, navOptions)
}
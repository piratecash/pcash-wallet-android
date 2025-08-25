package cash.p.terminal.ui_compose

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

internal  inline fun <reified T> getKoinInstance(): T {
    return object : KoinComponent {
        val value: T by inject()
    }.value
}

@Composable
fun Modifier.blockClicksBehind() = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() }
) { /* Do nothing */ }

@Composable
fun Modifier.oneLineHeight(textStyle: TextStyle) =
    this.height(with(LocalDensity.current) {
        textStyle.lineHeight.toDp()
    }).wrapContentHeight(Alignment.CenterVertically)
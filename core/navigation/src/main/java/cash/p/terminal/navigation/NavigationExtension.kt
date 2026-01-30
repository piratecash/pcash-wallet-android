package cash.p.terminal.navigation

import android.os.Parcelable
import android.util.Log
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import java.util.UUID

fun NavController.slideFromRight(directions: NavDirections) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_right)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_right)
        .build()

    navigate(directions, navOptions)
}

fun NavController.slideFromBottom(directions: NavDirections) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_bottom)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_bottom)
        .build()

    navigate(directions, navOptions)
}

fun NavController.slideFromRight(@IdRes resId: Int, input: Parcelable? = null) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_right)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_right)
        .build()

    val args = input?.let {
        bundleOf("input" to it)
    }
    navigate(resId, args, navOptions)
}

fun NavController.slideFromRight(@IdRes resId: Int, vararg pairs: Pair<String, Any?>) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_right)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_right)
        .build()

    navigate(resId, bundleOf(*pairs), navOptions)
}

fun <T : Parcelable> NavController.slideFromBottomForResult(
    @IdRes resId: Int,
    input: Parcelable? = null,
    onResult: (T) -> Unit
) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_bottom)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_bottom)
        .build()

    navigateForResult(resId, input, navOptions, onResult)
}

private fun <T : Parcelable> NavController.navigateForResult(
    resId: Int,
    input: Parcelable?,
    navOptions: NavOptions,
    onResult: (T) -> Unit,
) {
    val key = UUID.randomUUID().toString()
    getNavigationResultX(key, onResult)
    val bundle = bundleOf("resultKey" to key)
    input?.let {
        bundle.putParcelable("input", it)
    }
    navigate(resId, bundle, navOptions)
}

private fun <T : Parcelable> NavController.getNavigationResultX(
    key: String,
    onResult: (T) -> Unit
) {
    currentBackStackEntry?.let { backStackEntry ->
        backStackEntry.savedStateHandle.getLiveData<T>(key).observe(backStackEntry) {
            onResult.invoke(it)

            backStackEntry.savedStateHandle.remove<T>(key)
        }
    }
}

fun <T : Parcelable> NavController.slideFromRightForResult(
    @IdRes resId: Int,
    input: Parcelable? = null,
    onResult: (T) -> Unit
) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_right)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_right)
        .build()

    navigateForResult(resId, input, navOptions, onResult)
}

fun <T : Parcelable> NavController.setNavigationResultX(result: T) {
    val resultKey = currentBackStackEntry?.arguments?.getString("resultKey")

    if (resultKey == null) {
        Log.w("AAA", "No key registered to set the result")
    } else {
        previousBackStackEntry?.savedStateHandle?.set(resultKey, result)
    }
}

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

fun NavController.slideFromRightClearingBackStack(
    @IdRes resId: Int,
    @IdRes popUpToId: Int,
    input: Parcelable? = null
) {
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_from_right)
        .setExitAnim(android.R.anim.fade_out)
        .setPopEnterAnim(android.R.anim.fade_in)
        .setPopExitAnim(R.anim.slide_to_right)
        .setPopUpTo(popUpToId, inclusive = false)
        .build()

    val args = input?.let {
        bundleOf("input" to it)
    }
    navigate(resId, args, navOptions)
}

fun NavController.safeGetBackStackEntry(@IdRes destinationId: Int): NavBackStackEntry? {
    return try {
        getBackStackEntry(destinationId)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Tries to pop back stack. If pop fails, executes the fallback action.
 * Use this for graceful navigation recovery after process death.
 */
inline fun NavController.popBackStackOrExecute(
    @IdRes destinationId: Int? = null,
    inclusive: Boolean = false,
    onPopFailed: () -> Unit
) {
    val popped = if (destinationId != null) {
        popBackStack(destinationId, inclusive)
    } else {
        popBackStack()
    }
    if (!popped) {
        onPopFailed()
    }
}
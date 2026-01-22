package cash.p.terminal.navigation

import android.os.Parcelable
import androidx.navigation.NavController
import kotlinx.parcelize.Parcelize

@Parcelize
data class QrScannerInput(
    val title: String,
    val showPasteButton: Boolean = false,
    val allowGalleryWithoutPremium: Boolean = false
) : Parcelable

@Parcelize
data class QrScannerResult(val text: String) : Parcelable

fun NavController.openQrScanner(
    title: String,
    showPasteButton: Boolean = false,
    allowGalleryWithoutPremium: Boolean = false,
    onResult: (String) -> Unit,
) {
    slideFromBottomForResult<QrScannerResult>(
        R.id.qrScannerFragment,
        QrScannerInput(title, showPasteButton, allowGalleryWithoutPremium)
    ) { result ->
        onResult(result.text)
    }
}

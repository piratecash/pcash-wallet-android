package cash.p.terminal.core.managers

import androidx.annotation.StringRes
import cash.p.terminal.R

@StringRes
fun Throwable.toSeedQrErrorStringRes(): Int = when (this) {
    is SeedPhraseQrCrypto.QrDecodeError.InvalidFormat -> R.string.seed_qr_invalid_format
    else -> R.string.seed_qr_decryption_failed
}

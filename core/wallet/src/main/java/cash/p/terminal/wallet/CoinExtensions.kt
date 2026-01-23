package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin

fun coinImageUrl(coinUid: String): String =
    "https://p.cash/storage/coins/$coinUid/image.png"

val Coin.imageUrl: String
    get() = coinImageUrl(uid)

val Coin.alternativeImageUrl: String?
    get() = image

val Coin.imagePlaceholder: Int
    get() = R.drawable.coin_placeholder

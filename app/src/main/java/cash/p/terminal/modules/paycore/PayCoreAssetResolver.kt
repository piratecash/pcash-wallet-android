package cash.p.terminal.modules.paycore

import cash.p.terminal.modules.paycore.PayCoreAssets.RUB_COIN_UID

object PayCoreAssetResolver {
    fun coinCode(coinUid: String): String? = when (coinUid) {
        RUB_COIN_UID -> "RUB"
        else -> null
    }

    fun coinName(coinUid: String): String? = when (coinUid) {
        RUB_COIN_UID -> "Russian Ruble"
        else -> null
    }
}

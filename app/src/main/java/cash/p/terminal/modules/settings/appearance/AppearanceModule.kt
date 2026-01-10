package cash.p.terminal.modules.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.annotations.SerializedName
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

object AppearanceModule {

    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val launchScreenService = LaunchScreenService(App.localStorage)
            val appIconService = AppIconService(App.localStorage)
            val themeService = ThemeService(App.localStorage)
            return AppearanceViewModel(
                launchScreenService,
                appIconService,
                themeService,
                App.balanceViewTypeManager,
                App.localStorage,
            ) as T
        }
    }

}

enum class AppIconCategory {
    OUR, OTHER
}

enum class AppIcon(
    val foreground: Int,
    val background: Int,
    val titleText: String,
    val category: AppIconCategory
) : WithTranslatableTitle {
    // OUR ICON
    Main(R.drawable.launcher_pcash_foreground, R.drawable.launcher_pcash_background, "P.CASH", AppIconCategory.OUR),
    Pirate(R.drawable.launcher_pirate_foreground, R.drawable.launcher_pirate_background, "PIRATE", AppIconCategory.OUR),
    Cosa(R.drawable.launcher_cosa_foreground, R.drawable.launcher_cosa_background, "COSA", AppIconCategory.OUR),
    // OTHER (crypto icons)
    Btc(R.drawable.launcher_btc_foreground, R.drawable.launcher_btc_background, "BTC", AppIconCategory.OTHER),
    Eth(R.drawable.launcher_eth_foreground, R.drawable.launcher_eth_background, "ETH", AppIconCategory.OTHER),
    Bnb(R.drawable.launcher_bnb_foreground, R.drawable.launcher_bnb_background, "BNB", AppIconCategory.OTHER),
    Doge(R.drawable.launcher_doge_foreground, R.drawable.launcher_doge_background, "DOGE", AppIconCategory.OTHER),
    Ltc(R.drawable.launcher_ltc_foreground, R.drawable.launcher_ltc_background, "LTC", AppIconCategory.OTHER),
    Xmr(R.drawable.launcher_xmr_foreground, R.drawable.launcher_xmr_background, "XMR", AppIconCategory.OTHER),
    Ton(R.drawable.launcher_ton_foreground, R.drawable.launcher_ton_background, "TON", AppIconCategory.OTHER),
    Trx(R.drawable.launcher_trx_foreground, R.drawable.launcher_trx_background, "TRX", AppIconCategory.OTHER),
    Sol(R.drawable.launcher_sol_foreground, R.drawable.launcher_sol_background, "SOL", AppIconCategory.OTHER),
    Matic(R.drawable.launcher_matic_foreground, R.drawable.launcher_matic_background, "MATIC", AppIconCategory.OTHER),
    Arb(R.drawable.launcher_arb_foreground, R.drawable.launcher_arb_background, "ARB", AppIconCategory.OTHER),
    Op(R.drawable.launcher_op_foreground, R.drawable.launcher_op_background, "OP", AppIconCategory.OTHER),
    Base(R.drawable.launcher_base_foreground, R.drawable.launcher_base_background, "BASE", AppIconCategory.OTHER),
    Zk(R.drawable.launcher_zk_foreground, R.drawable.launcher_zk_background, "ZK", AppIconCategory.OTHER),
    Avax(R.drawable.launcher_avax_foreground, R.drawable.launcher_avax_background, "AVAX", AppIconCategory.OTHER),
    Dash(R.drawable.launcher_dash_foreground, R.drawable.launcher_dash_background, "DASH", AppIconCategory.OTHER),
    Xlm(R.drawable.launcher_xlm_foreground, R.drawable.launcher_xlm_background, "XLM", AppIconCategory.OTHER),
    Zec(R.drawable.launcher_zec_foreground, R.drawable.launcher_zec_background, "ZEC", AppIconCategory.OTHER),
    Ftm(R.drawable.launcher_ftm_foreground, R.drawable.launcher_ftm_background, "FTM", AppIconCategory.OTHER),
    Gno(R.drawable.launcher_gno_foreground, R.drawable.launcher_gno_background, "GNO", AppIconCategory.OTHER),
    Xec(R.drawable.launcher_xec_foreground, R.drawable.launcher_xec_background, "XEC", AppIconCategory.OTHER),
    Bch(R.drawable.launcher_bch_foreground, R.drawable.launcher_bch_background, "BCH", AppIconCategory.OTHER);

    override val title: TranslatableString
        get() = TranslatableString.PlainString(titleText)

    // Use namespace (not applicationId) because activity-alias names are based on manifest namespace
    val launcherName: String
        get() = "cash.p.terminal.${this.name}LauncherAlias"

    companion object {
        private val map = entries.associateBy(AppIcon::name)
        private val titleMap = entries.associateBy(AppIcon::titleText)

        fun fromString(type: String?): AppIcon? = map[type]
        fun fromTitle(title: String?): AppIcon? = titleMap[title]
    }
}

enum class PriceChangeInterval(val raw: String, override val title: TranslatableString):
    WithTranslatableTitle {
    @SerializedName("hour_24")
    LAST_24H("hour_24", TranslatableString.ResString(R.string.Market_PriceChange_24H)),
    @SerializedName("midnight_utc")
    FROM_UTC_MIDNIGHT("midnight_utc", TranslatableString.ResString(R.string.Market_PriceChange_Utc));

    companion object {
        fun fromRaw(raw: String): PriceChangeInterval? {
            return entries.find { it.raw == raw }
        }
    }
}
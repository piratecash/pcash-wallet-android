package cash.p.terminal.modules.paycore.selectbank

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.paycore.PayCoreBankResponse
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class PayCoreBankSwapSetting(
    private val banks: List<PayCoreBankResponse>,
    private val selectedBank: PayCoreBankResponse?,
) : ISwapSetting {

    override val id = ID

    override val titleRes: Int
        get() = R.string.paycore_select_bank

    val hasBanks: Boolean
        get() = banks.isNotEmpty()

    @Composable
    override fun GetContent(
        navController: NavController,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit,
    ) {
        PayCoreBankValidationEffect(
            banksUnavailable = banks.isEmpty(),
            selectedBank = selectedBank,
            onError = onError,
        )

        if (banks.isEmpty()) {
            PayCoreBanksUnavailable()
        }
    }

    override fun LazyListScope.addContentItems(
        navController: NavController,
        value: Any?,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    ) {
        addBankItems(
            value = value,
            query = "",
            onError = onError,
            onValueChange = onValueChange,
        )
    }

    fun LazyListScope.addBankItems(
        value: Any?,
        query: String,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    ) {
        val currentBank = selectedBank(value)

        item(key = "$id:validation") {
            PayCoreBankValidationEffect(
                banksUnavailable = banks.isEmpty(),
                selectedBank = currentBank,
                onError = onError,
            )
        }

        if (banks.isEmpty()) {
            item(key = "$id:unavailable") {
                PayCoreBanksUnavailable()
            }
            return
        }

        val filteredBanks = banks.filter { it.name.contains(query, ignoreCase = true) }
        if (filteredBanks.isEmpty()) {
            item(key = "$id:no_results") {
                PayCoreBanksNotFound()
            }
            return
        }

        payCoreBankSectionItems(
            banks = filteredBanks,
            keyPrefix = id,
            selectedBankId = currentBank?.id,
            showSelection = true,
            onBankClick = { bank ->
                onValueChange(bank)
                // The validation item may be off-screen when a bank is tapped.
                onError(null)
            },
        )
    }

    private fun selectedBank(value: Any?): PayCoreBankResponse? {
        val bankId = (value as? PayCoreBankResponse)?.id ?: selectedBank?.id ?: return null
        return banks.firstOrNull { it.id == bankId }
    }

    companion object {
        const val ID = "paycore_bank"

        fun selectedBank(settings: Map<String, Any?>): PayCoreBankResponse? =
            settings[ID] as? PayCoreBankResponse
    }
}

class PayCoreBankNotSelectedException : IllegalStateException("PayCore bank is not selected")

class PayCoreBanksUnavailableException : IllegalStateException("PayCore banks are unavailable")

@Composable
private fun PayCoreBankValidationEffect(
    banksUnavailable: Boolean,
    selectedBank: PayCoreBankResponse?,
    onError: (Throwable?) -> Unit,
) {
    val currentOnError by rememberUpdatedState(onError)

    LaunchedEffect(banksUnavailable, selectedBank) {
        val validationError = when {
            banksUnavailable -> PayCoreBanksUnavailableException()
            selectedBank == null -> PayCoreBankNotSelectedException()
            else -> null
        }
        currentOnError(validationError)
    }
}

@Composable
private fun PayCoreBankSwapSettingPreviewContent(
    banks: List<PayCoreBankResponse>,
    selectedBank: PayCoreBankResponse?,
) {
    val navController = rememberNavController()
    val setting = PayCoreBankSwapSetting(banks, selectedBank)

    LazyColumn {
        with(setting) {
            addContentItems(
                navController = navController,
                value = selectedBank,
                onError = {},
                onValueChange = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingLoadingPreview() {
    ComposeAppTheme {
        PayCoreBankSwapSettingPreviewContent(
            banks = emptyList(),
            selectedBank = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingListPreview() {
    ComposeAppTheme {
        val banks = payCoreBankPreviewItems()
        PayCoreBankSwapSettingPreviewContent(
            banks = banks,
            selectedBank = banks.first(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingEmptyPreview() {
    ComposeAppTheme {
        PayCoreBankSwapSettingPreviewContent(
            banks = emptyList(),
            selectedBank = null,
        )
    }
}

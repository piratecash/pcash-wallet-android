package cash.p.terminal.core.ethereum

import cash.p.terminal.R
import cash.p.terminal.core.EvmError
import cash.p.terminal.core.Warning
import cash.p.terminal.core.convertedError
import cash.p.terminal.core.providers.Translator
import cash.p.terminal.modules.evmfee.FeeSettingsError
import cash.p.terminal.modules.evmfee.FeeSettingsWarning

class CautionViewItemFactory(
    private val baseCoinService: EvmCoinService
) {
    fun cautionViewItems(warnings: List<Warning>, errors: List<Throwable>): List<CautionViewItem> {
        return warnings.map { cautionViewItem(it) } + errors.map { cautionViewItem(it) }
    }

    private fun cautionViewItem(warning: Warning): CautionViewItem {
        return when (warning) {
            FeeSettingsWarning.RiskOfGettingStuck -> {
                CautionViewItem(
                    Translator.getString(R.string.FeeSettings_RiskOfGettingStuck_Title),
                    Translator.getString(R.string.FeeSettings_RiskOfGettingStuck),
                    CautionViewItem.Type.Warning
                )
            }
            FeeSettingsWarning.RiskOfGettingStuckLegacy -> {
                CautionViewItem(
                    Translator.getString(R.string.FeeSettings_RiskOfGettingStuckLegacy_Title),
                    Translator.getString(R.string.FeeSettings_RiskOfGettingStuckLegacy),
                    CautionViewItem.Type.Warning
                )
            }
            FeeSettingsWarning.Overpricing -> {
                CautionViewItem(
                    Translator.getString(R.string.FeeSettings_Overpricing_Title),
                    Translator.getString(R.string.FeeSettings_Overpricing),
                    CautionViewItem.Type.Warning
                )
            }
            else -> {
                CautionViewItem(
                    Translator.getString(R.string.EthereumTransaction_Warning_Title),
                    warning.javaClass.simpleName,
                    CautionViewItem.Type.Warning
                )
            }
        }
    }

    private fun cautionViewItem(error: Throwable): CautionViewItem {
        return when (error) {
            FeeSettingsError.InsufficientBalance -> {
                CautionViewItem(
                    Translator.getString(R.string.EthereumTransaction_Error_InsufficientBalance_Title),
                    Translator.getString(
                        R.string.EthereumTransaction_Error_InsufficientBalanceForFee,
                        baseCoinService.token.coin.code
                    ),
                    CautionViewItem.Type.Error
                )
            }
            FeeSettingsError.UsedNonce -> {
                CautionViewItem(
                    Translator.getString(R.string.SendEvmSettings_Error_NonceUsed_Title),
                    Translator.getString(R.string.SendEvmSettings_Error_NonceUsed),
                    CautionViewItem.Type.Error
                )
            }
            else -> {
                val (title, text) = convertError(error)
                CautionViewItem(title, text, CautionViewItem.Type.Error)
            }
        }
    }

    private fun convertError(error: Throwable): Pair<String, String> =
        when (val convertedError = error.convertedError) {
            is EvmError.InsufficientBalanceWithFee -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_Title),
                    Translator.getString(
                        R.string.EthereumTransaction_Error_InsufficientBalanceForFee,
                        baseCoinService.token.coin.code
                    )
                )
            }
            is EvmError.ExecutionReverted -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_Title),
                    Translator.getString(
                        R.string.EthereumTransaction_Error_ExecutionReverted,
                        convertedError.message ?: ""
                    )
                )
            }
            is EvmError.CannotEstimateSwap -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_CannotEstimate_Title),
                    Translator.getString(
                        R.string.EthereumTransaction_Error_CannotEstimate,
                        baseCoinService.token.coin.code
                    )
                )
            }
            is EvmError.LowerThanBaseGasLimit -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_LowerThanBaseGasLimit_Title),
                    Translator.getString(R.string.EthereumTransaction_Error_LowerThanBaseGasLimit)
                )
            }
            is EvmError.InsufficientLiquidity -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_InsufficientLiquidity_Title),
                    Translator.getString(R.string.EthereumTransaction_Error_InsufficientLiquidity)
                )
            }
            else -> {
                Pair(
                    Translator.getString(R.string.EthereumTransaction_Error_Title),
                    convertedError.message ?: convertedError.javaClass.simpleName
                )
            }
        }
}

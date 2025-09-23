package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.adapters.BitcoinFeeInfo
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.factories.FeeRateProviderFactory
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.evmfee.EvmSettingsInput
import cash.p.terminal.modules.fee.HSFeeRaw
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.bitcoin.SendBitcoinAddressService
import cash.p.terminal.modules.send.bitcoin.SendBitcoinAmountService
import cash.p.terminal.modules.send.bitcoin.SendBitcoinFeeRateService
import cash.p.terminal.modules.send.bitcoin.SendBitcoinFeeService
import cash.p.terminal.modules.send.bitcoin.SendBitcoinPluginService
import cash.p.terminal.modules.send.bitcoin.advanced.FeeRateCaution
import cash.p.terminal.modules.send.bitcoin.settings.SendBtcSettingsViewModel
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class SendTransactionServiceBitcoin(
    token: Token
) : ISendTransactionService<ISendBitcoinAdapter>(token) {

    private val feeService = SendBitcoinFeeService(adapter)
    private val provider = FeeRateProviderFactory.provider(token.blockchainType)!!
    private val feeRateService = SendBitcoinFeeRateService(provider)
    private var bitcoinFeeInfo = feeService.bitcoinFeeInfoFlow.value
    private val amountService =
        SendBitcoinAmountService(adapter, wallet.coin.code, AmountValidator())
    private val addressService = SendBitcoinAddressService(adapter)
    private val localStorage: ILocalStorage by inject(ILocalStorage::class.java)
    private val pluginService = SendBitcoinPluginService(wallet.token.blockchainType)
    private val marketKit: MarketKitWrapper by inject(MarketKitWrapper::class.java)
    private val xRateService = XRateService(marketKit, App.currencyManager.baseCurrency)

    private var memo: String? = null
    private var changeToFirstInput: Boolean = false
    private var utxoFilters: UtxoFilters = UtxoFilters()
    private var networkFee: SendModule.AmountData? = null

    private val _sendTransactionSettingsFlow = MutableStateFlow(
        SendTransactionSettings.Common
    )
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()

    //    private var feeAmountData: SendModule.AmountData? = null
    private var cautions: List<CautionViewItem> = listOf()
    private var sendable = false
    private var loading = true
    private var fields = listOf<DataField>()

    override fun createState() = SendTransactionServiceState(
        networkFee = networkFee,
        cautions = cautions,
        sendable = sendable,
        loading = loading,
        fields = fields,
        availableBalance = calculateAvailableBalance(),
        extraFees = extraFees
    )

    val coinMaxAllowedDecimals = wallet.token.decimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    val blockchainType by adapter::blockchainType

    private var utxoExpertModeEnabled by localStorage::utxoExpertModeEnabled
    private var feeRateState = feeRateService.stateFlow.value
    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var pluginState = pluginService.stateFlow.value

    private var customUnspentOutputs: List<UnspentOutputInfo>? = null

    private var coinRate by mutableStateOf(xRateService.getRate(wallet.coin.uid))

    override fun start(coroutineScope: CoroutineScope) {
        feeRateService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedFeeRateState(it)
        }
        amountService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedAmountState(it)
        }
        addressService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedAddressState(it)
        }
        pluginService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedPluginState(it)
        }
        feeService.bitcoinFeeInfoFlow.collectWith(coroutineScope) {
            handleUpdatedFeeInfo(it)
        }
        xRateService.getRateFlow(wallet.coin.uid).collectWith(coroutineScope) {
            coinRate = it
        }
        localStorage.utxoExpertModeEnabledFlow.collectWith(coroutineScope) { enabled ->
            utxoExpertModeEnabled = enabled
            emitState()
        }

        coroutineScope.launch {
            feeRateService.start()
        }
    }

    private fun calculateAvailableBalance(): BigDecimal? = feeRateState.feeRate?.let {
        adapter.availableBalance(
            feeRate = it,
            address = addressState.validAddress?.hex,
            memo = memo,
            unspentOutputs = customUnspentOutputs,
            pluginData = pluginState.pluginData,
            changeToFirstInput = changeToFirstInput,
            utxoFilters = utxoFilters
        )
    }

    private fun handleUpdatedAmountState(amountState: SendBitcoinAmountService.State) {
        this.amountState = amountState

        feeService.setAmount(amountState.amount)

        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendBitcoinAddressService.State) {
        this.addressState = addressState

        amountService.setValidAddress(addressState.validAddress)
        feeService.setValidAddress(addressState.validAddress)

        emitState()
    }

    private fun handleUpdatedFeeRateState(feeRateState: SendBitcoinFeeRateService.State) {
        this.feeRateState = feeRateState
        sendable = feeRateState.canBeSend

        feeService.setFeeRate(feeRateState.feeRate)
        amountService.setFeeRate(feeRateState.feeRate)
        loading = false

        emitState()
    }

    private fun handleUpdatedPluginState(pluginState: SendBitcoinPluginService.State) {
        this.pluginState = pluginState

        feeService.setPluginData(pluginState.pluginData)
        amountService.setPluginData(pluginState.pluginData)
        addressService.setPluginData(pluginState.pluginData)

        emitState()
    }

    private fun handleUpdatedFeeInfo(info: BitcoinFeeInfo?) {
        bitcoinFeeInfo = info

        refreshNetworkFee()

        emitState()
    }

    private fun refreshNetworkFee() {
        networkFee = bitcoinFeeInfo?.fee?.let { fee ->
            getAmountData(CoinValue(token, fee))
        }
    }

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Btc)

        memo = data.memo
        changeToFirstInput = data.changeToFirstInput
        utxoFilters = data.utxoFilters

        feeRateService.setRecommendedAndMin(data.recommendedGasRate, data.recommendedGasRate)

        feeService.setMemo(memo)
        feeService.setChangeToFirstInput(changeToFirstInput)
        feeService.setUtxoFilters(utxoFilters)

        amountService.setMemo(memo)
        amountService.setUserMinimumSendAmount(data.minimumSendAmount)
        amountService.setChangeToFirstInput(changeToFirstInput)
        amountService.setUtxoFilters(utxoFilters)
        amountService.setAmount(data.amount)

        addressService.setAddress(Address(data.address))

        setExtraFeesMap(data.feesMap)
    }

    @Composable
    override fun GetSettingsContent(navController: NavController) {
        val sendSettingsViewModel = viewModel<SendBtcSettingsViewModel>(
            factory = SendBtcSettingsViewModel.Factory(feeRateService, feeService, token)
        )

        SendBtcFeeSettingsScreen(navController, sendSettingsViewModel)
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult.Btc =
        withContext(Dispatchers.IO) {
            try {
                val transactionRecord = adapter.send(
                    amount = amountState.amount!!,
                    address = addressState.validAddress?.hex!!,
                    memo = memo,
                    feeRate = feeRateState.feeRate!!,
                    unspentOutputs = null,
                    pluginData = null,
                    transactionSorting = null,
                    rbfEnabled = false,
                    changeToFirstInput = changeToFirstInput,
                    utxoFilters = utxoFilters
                )
                SendTransactionResult.Btc(transactionRecord)
            } catch (e: Throwable) {
                cautions = listOf(createCaution(e))
                emitState()
                throw e
            }
        }
}

@Composable
fun SendBtcFeeSettingsScreen(
    navController: NavController,
    viewModel: SendBtcSettingsViewModel
) {
    val uiState = viewModel.uiState

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .background(color = ComposeAppTheme.colors.tyler)
    ) {
        AppBar(
            title = stringResource(R.string.SendEvmSettings_Title),
            navigationIcon = {
                HsIconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "back button",
                        tint = ComposeAppTheme.colors.jacob
                    )
                }
            },
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Reset),
                    enabled = uiState.resetEnabled,
                    tint = ComposeAppTheme.colors.jacob,
                    onClick = {
                        viewModel.reset()
                    }
                )
            )
        )

        VSpacer(12.dp)
        CellUniversalLawrenceSection(
            listOf {
                HSFeeRaw(
                    coinCode = viewModel.token.coin.code,
                    coinDecimal = viewModel.coinMaxAllowedDecimals,
                    fee = uiState.fee,
                    amountInputType = AmountInputType.COIN,
                    rate = uiState.rate,
                    navController = navController
                )
            }
        )

        if (viewModel.feeRateChangeable) {
            VSpacer(24.dp)
            EvmSettingsInput(
                title = stringResource(R.string.FeeSettings_FeeRate),
                info = stringResource(R.string.FeeSettings_FeeRate_Info),
                value = uiState.feeRate?.toBigDecimal() ?: BigDecimal.ZERO,
                decimals = 0,
                caution = uiState.feeRateCaution,
                navController = navController,
                onValueChange = {
                    viewModel.updateFeeRate(it.toInt())
                },
                onClickIncrement = {
                    viewModel.incrementFeeRate()
                },
                onClickDecrement = {
                    viewModel.decrementFeeRate()
                }
            )
            InfoText(
                text = stringResource(R.string.FeeSettings_FeeRate_RecommendedInfo),
            )
        }

        uiState.feeRateCaution?.let {
            FeeRateCaution(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp
                ),
                feeRateCaution = it
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

}
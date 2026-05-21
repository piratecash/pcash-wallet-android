package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.paycore.PayCoreProvider

class SwapProvidersRegistry(
    changeNowProvider: ChangeNowProvider,
    quickexProvider: QuickexProvider,
    stonFiProvider: StonFiProvider,
    payCoreProvider: PayCoreProvider,
) {
    val providers: List<IMultiSwapProvider> = listOf(
        OneInchProvider,
        PancakeSwapProvider,
        PancakeSwapV3Provider,
        QuickSwapProvider,
        UniswapProvider,
        UniswapV3Provider,
        changeNowProvider,
        quickexProvider,
        ThorChainProvider,
        MayaProvider,
        AllBridgeProvider,
        stonFiProvider,
        payCoreProvider
    )

    fun findById(id: String): IMultiSwapProvider? =
        providers.firstOrNull { it.id == id }
}

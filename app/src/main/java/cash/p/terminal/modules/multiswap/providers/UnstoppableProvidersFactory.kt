package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider

/**
 * Builds one wrapper per registrable Unstoppable sub-provider, sharing the injected collaborators.
 * A concrete, constructor-injectable type — rather than a raw `List<IMultiSwapProvider>` Koin binding —
 * so the Koin graph verification can resolve [SwapProvidersRegistry]'s dependency (verify() cannot map a
 * generic `List` constructor parameter to a binding).
 */
class UnstoppableProvidersFactory(
    private val walletUseCase: WalletUseCase,
    private val repository: UnstoppableRepository,
    private val marketKit: MarketKitWrapper,
    private val accountManager: IAccountManager,
    private val dispatcherProvider: DispatcherProvider,
    private val providerSupport: OffChainSwapProviderSupport,
) {
    fun create(): List<IMultiSwapProvider> = UnstoppableProvider.registrable().map { descriptor ->
        if (descriptor.isEvm) {
            UnstoppableEvmSwapProvider(
                descriptor, walletUseCase, repository, marketKit, accountManager, dispatcherProvider, providerSupport,
            )
        } else {
            UnstoppableSwapProvider(
                descriptor, walletUseCase, repository, marketKit, accountManager, dispatcherProvider, providerSupport,
            )
        }
    }
}

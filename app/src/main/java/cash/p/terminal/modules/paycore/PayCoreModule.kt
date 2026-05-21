package cash.p.terminal.modules.paycore

import cash.p.terminal.modules.paycore.exchange.PayCoreExchangeDetailViewModel
import cash.p.terminal.modules.paycore.payment.PayCorePaymentParams
import cash.p.terminal.modules.paycore.payment.PayCorePaymentViewModel
import cash.p.terminal.modules.paycore.selectbank.PayCoreSelectBankViewModel
import cash.p.terminal.modules.paycore.verification.PayCoreVerificationViewModel
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val payCoreModule = module {
    single { PayCoreSignatureHelper(get()) }
    singleOf(::PayCoreApiService)
    singleOf(::PayCoreFeatureToggle)
    singleOf(::PayCoreSecureStorage)
    singleOf(::PayCoreProvider)
    singleOf(::PayCoreStatusRepository) bind SwapProviderTransactionStatusRepository::class
    single(named(SwapProvider.PAYCORE)) { get<PayCoreStatusRepository>() as SwapProviderTransactionStatusRepository }
    viewModel { params -> PayCoreVerificationViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> PayCorePaymentViewModel(get(), get(), get(), params.get<PayCorePaymentParams>()) }
    viewModel { params -> PayCoreExchangeDetailViewModel(params.get(), get(), get(), get()) }
    viewModelOf(::PayCoreSelectBankViewModel)
}

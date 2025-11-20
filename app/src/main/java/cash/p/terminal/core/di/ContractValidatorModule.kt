package cash.p.terminal.core.di

import cash.p.terminal.core.address.AlphaAmlAddressValidator
import cash.p.terminal.modules.send.contractvalidators.ContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.EvmContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.ExcludedContractValidator
import cash.p.terminal.modules.send.contractvalidators.SolanaContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.TonContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.TronContractAddressValidator
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val contractValidatorModule = module {
    single<ContractAddressValidator>(named(BlockchainType.Ethereum.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.BinanceSmartChain.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Polygon.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Avalanche.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Optimism.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Base.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.ZkSync.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.ArbitrumOne.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Gnosis.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Fantom.uid)) {
        EvmContractAddressValidator(
            get(),
            get()
        )
    }

    single<ContractAddressValidator>(named(BlockchainType.Solana.uid)) {
        SolanaContractAddressValidator(
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Tron.uid)) {
        TronContractAddressValidator(
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Ton.uid)) {
        TonContractAddressValidator(
            get()
        )
    }
    singleOf(::ExcludedContractValidator)
    singleOf(::AlphaAmlAddressValidator)
}
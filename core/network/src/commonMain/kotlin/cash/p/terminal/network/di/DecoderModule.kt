package cash.p.terminal.network.di

import cash.p.terminal.network.data.Decoder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val decoderModule = module {
    singleOf(::Decoder)
}

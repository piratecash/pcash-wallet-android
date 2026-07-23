package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.paycore.PayCoreProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnstoppableProviderTest {

    @Test
    fun excluded_containsQuickExAndExolix() {
        assertEquals(setOf(UnstoppableProvider.QuickEx, UnstoppableProvider.Exolix), UnstoppableProvider.EXCLUDED)
    }

    @Test
    fun registrable_neverYieldsAnExcludedEntry_andYieldsTheOtherEight() {
        val registrable = UnstoppableProvider.registrable()

        assertTrue(registrable.none { it in UnstoppableProvider.EXCLUDED })
        assertEquals(UnstoppableProvider.entries.size - UnstoppableProvider.EXCLUDED.size, registrable.size)
        assertEquals(8, registrable.size)
    }

    @Test
    fun registrable_idsDoNotCollideWithDirectlyRegisteredProviderIds() {
        // Mirrors the directly-registered ids in SwapProvidersRegistry.providers (everything in
        // that list outside the `+ unstoppableProviders` tail). Guards against a future Unstoppable
        // sub-provider silently duplicating an already-integrated provider's quotes.
        // ChangeNowProvider/QuickexProvider/ExolixProvider/StonFiProvider take constructor
        // dependencies, so their ids are hardcoded here rather than constructed.
        val directlyRegisteredIds = setOf(
            OneInchProvider.id,
            PancakeSwapProvider.id,
            PancakeSwapV3Provider.id,
            QuickSwapProvider.id,
            UniswapProvider.id,
            UniswapV3Provider.id,
            "changenow",
            "quickex",
            "exolix",
            ThorChainProvider.id,
            MayaProvider.id,
            AllBridgeProvider.id,
            "stonfi",
            PayCoreProvider.ID,
        )

        val unstoppableIds = UnstoppableProvider.registrable().map { it.id }

        assertTrue(unstoppableIds.none { it in directlyRegisteredIds })
    }

    @Test
    fun displayTitle_knownApiId_returnsDescriptorTitle() {
        assertEquals("Barter", UnstoppableProvider.displayTitle(UnstoppableProvider.Barter.apiId))
    }

    @Test
    fun displayTitle_unknownApiId_returnsRawId() {
        assertEquals("SOME_REMOVED_PROVIDER", UnstoppableProvider.displayTitle("SOME_REMOVED_PROVIDER"))
    }

    @Test
    fun displayTitle_nullApiId_returnsNull() {
        assertNull(UnstoppableProvider.displayTitle(null))
    }
}

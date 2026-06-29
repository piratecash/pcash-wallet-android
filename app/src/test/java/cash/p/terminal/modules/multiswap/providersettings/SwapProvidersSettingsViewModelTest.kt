package cash.p.terminal.modules.multiswap.providersettings

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.paycore.PayCoreFeatureToggle
import cash.p.terminal.modules.paycore.PayCoreProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapProvidersSettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val disabledIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    private val registry = mockk<SwapProvidersRegistry> {
        every { providers } returns listOf(
            provider(PayCoreProvider.ID, "PayCore"),
            provider("quickex", "Quickex"),
        )
    }
    private val repository = mockk<SwapProvidersRepository>(relaxed = true) {
        every { disabledIds } returns disabledIdsFlow
        every { isDisabled(any()) } returns false
        every { isMandatory(any()) } returns false
    }
    private val payCoreFeatureToggle = mockk<PayCoreFeatureToggle>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun createState_payCoreDisabled_excludesPayCore() {
        every { payCoreFeatureToggle.isEnabled() } returns false

        val viewModel = SwapProvidersSettingsViewModel(registry, repository, payCoreFeatureToggle)

        assertFalse(viewModel.uiState.items.any { it.id == PayCoreProvider.ID })
        assertTrue(viewModel.uiState.items.any { it.id == "quickex" })
    }

    @Test
    fun createState_payCoreEnabled_includesPayCore() {
        every { payCoreFeatureToggle.isEnabled() } returns true

        val viewModel = SwapProvidersSettingsViewModel(registry, repository, payCoreFeatureToggle)

        assertTrue(viewModel.uiState.items.any { it.id == PayCoreProvider.ID })
        assertTrue(viewModel.uiState.items.any { it.id == "quickex" })
    }

    private fun provider(providerId: String, providerTitle: String): IMultiSwapProvider =
        mockk(relaxed = true) {
            every { id } returns providerId
            every { title } returns providerTitle
            every { icon } returns 0
        }
}

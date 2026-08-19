package cash.p.terminal.modules.zcashconfigure

import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.ZcashHeightResult
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashConfigureViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val getZcashHeightUseCase = mockk<GetZcashHeightUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ZcashConfigureViewModel(getZcashHeightUseCase)

    private fun TestScope.viewModelWithFailedDateLookup(date: LocalDate): ZcashConfigureViewModel {
        val viewModel = createViewModel()
        coEvery { getZcashHeightUseCase(date) } returns ZcashHeightResult.NetworkError
        viewModel.setInitialConfig(TokenConfig(birthdayHeight = "2300000", restoreAsNew = false))
        viewModel.onDatePicked(date)
        advanceUntilIdle()
        return viewModel
    }

    // GetZcashHeightUseCaseImpl catches Throwable, so it never honours cancellation.
    private fun stubSlowLookup(date: LocalDate, result: ZcashHeightResult) {
        coEvery { getZcashHeightUseCase(date) } coAnswers {
            withContext(NonCancellable) { delay(1_000) }
            result
        }
    }

    @Test
    fun onDatePicked_success_setsBirthdayHeightFromResult() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val date = LocalDate.of(2020, 1, 1)
            coEvery { getZcashHeightUseCase(date) } returns ZcashHeightResult.Success(1_234_567L)

            viewModel.onDatePicked(date)
            advanceUntilIdle()

            assertEquals("1234567", viewModel.uiState.birthdayHeight)
            assertNull(viewModel.uiState.errorHeight)
        }

    @Test
    fun onDatePicked_notFound_setsInvalidHeightError() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val date = LocalDate.of(2020, 1, 1)
            coEvery { getZcashHeightUseCase(date) } returns ZcashHeightResult.NotFound

            viewModel.onDatePicked(date)
            advanceUntilIdle()

            assertEquals(
                Translator.getString(R.string.invalid_height),
                viewModel.uiState.errorHeight
            )
        }

    @Test
    fun onDatePicked_networkError_setsConnectionErrorMessage() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val date = LocalDate.of(2020, 1, 1)
            coEvery { getZcashHeightUseCase(date) } returns ZcashHeightResult.NetworkError

            viewModel.onDatePicked(date)
            advanceUntilIdle()

            assertEquals(
                Translator.getString(R.string.blockchair_height_by_date_connection_error),
                viewModel.uiState.errorHeight
            )
        }

    @Test
    fun initialState_noConfig_noModeSelectedAndDoneDisabled() {
        val viewModel = createViewModel()

        assertNull(viewModel.uiState.mode)
        assertFalse(viewModel.uiState.doneEnabled)
    }

    @Test
    fun onModeSelect_newWallet_enablesDone() {
        val viewModel = createViewModel()

        viewModel.onModeSelect(RestoreHeightMode.NewWallet)

        assertEquals(RestoreHeightMode.NewWallet, viewModel.uiState.mode)
        assertTrue(viewModel.uiState.doneEnabled)
    }

    @Test
    fun setInitialConfig_savedHeight_selectsExistingWalletMode() {
        val viewModel = createViewModel()

        viewModel.setInitialConfig(
            TokenConfig(birthdayHeight = "2300000", restoreAsNew = false)
        )

        assertEquals(RestoreHeightMode.ExistingWallet, viewModel.uiState.mode)
        assertTrue(viewModel.uiState.doneEnabled)
    }

    @Test
    fun setBirthdayHeight_afterModeSelected_keepsSelectedMode() {
        val viewModel = createViewModel()

        viewModel.onModeSelect(RestoreHeightMode.ExistingWallet)
        viewModel.setBirthdayHeight("2300000")

        assertEquals(RestoreHeightMode.ExistingWallet, viewModel.uiState.mode)
        assertTrue(viewModel.uiState.doneEnabled)
    }

    @Test
    fun onDatePicked_lookupFailsWithSavedHeight_putsPickedDateIntoField() =
        runTest(dispatcher) {
            val date = LocalDate.of(2020, 1, 1)

            val viewModel = viewModelWithFailedDateLookup(date)

            assertEquals("2020-01-01", viewModel.uiState.birthdayHeight)
        }

    @Test
    fun onDoneClick_afterFailedDateLookupFailsAgain_keepsErrorAndDoesNotClose() =
        runTest(dispatcher) {
            val date = LocalDate.of(2020, 1, 1)
            val viewModel = viewModelWithFailedDateLookup(date)

            viewModel.onDoneClick()
            advanceUntilIdle()

            assertNull(viewModel.uiState.closeWithResult)
            assertEquals(
                Translator.getString(R.string.blockchair_height_by_date_connection_error),
                viewModel.uiState.errorHeight
            )
        }

    @Test
    fun onDoneClick_afterFailedDateLookupSucceedsOnRetry_closesWithResolvedHeight() =
        runTest(dispatcher) {
            val date = LocalDate.of(2020, 1, 1)
            val viewModel = viewModelWithFailedDateLookup(date)

            coEvery { getZcashHeightUseCase(date) } returns ZcashHeightResult.Success(1_234_567L)
            viewModel.onDoneClick()
            advanceUntilIdle()

            assertEquals("1234567", viewModel.uiState.closeWithResult?.birthdayHeight)
            assertNull(viewModel.uiState.errorHeight)
        }

    @Test
    fun onDatePicked_slowFailedLookupCompletesAfterNewerPick_keepsNewerResult() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val slowDate = LocalDate.of(2020, 1, 1)
            val latestDate = LocalDate.of(2021, 2, 2)
            stubSlowLookup(slowDate, ZcashHeightResult.NetworkError)
            coEvery { getZcashHeightUseCase(latestDate) } returns
                ZcashHeightResult.Success(1_234_567L)

            viewModel.onDatePicked(slowDate)
            advanceTimeBy(100)
            viewModel.onDatePicked(latestDate)
            advanceUntilIdle()

            assertEquals("1234567", viewModel.uiState.birthdayHeight)
            assertNull(viewModel.uiState.errorHeight)
        }

    @Test
    fun onDoneClick_newerDatePickedDuringRetry_doesNotCloseWithStaleHeight() =
        runTest(dispatcher) {
            val slowDate = LocalDate.of(2020, 1, 1)
            val latestDate = LocalDate.of(2021, 2, 2)
            val viewModel = viewModelWithFailedDateLookup(slowDate)
            stubSlowLookup(slowDate, ZcashHeightResult.Success(1L))
            coEvery { getZcashHeightUseCase(latestDate) } returns
                ZcashHeightResult.Success(1_234_567L)

            viewModel.onDoneClick()
            advanceTimeBy(100)
            viewModel.onDatePicked(latestDate)
            advanceUntilIdle()

            assertNull(viewModel.uiState.closeWithResult)
            assertEquals("1234567", viewModel.uiState.birthdayHeight)
        }

    @Test
    fun setBirthdayHeight_duringPendingDateLookup_keepsTypedText() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            val date = LocalDate.of(2020, 1, 1)
            stubSlowLookup(date, ZcashHeightResult.NetworkError)

            viewModel.onDatePicked(date)
            advanceTimeBy(100)
            viewModel.setBirthdayHeight("2400000")
            advanceUntilIdle()

            assertEquals("2400000", viewModel.uiState.birthdayHeight)
            assertNull(viewModel.uiState.errorHeight)
        }

    @Test
    fun onModeSelect_duringPendingDoneRetry_doesNotCloseWithStaleHeight() =
        runTest(dispatcher) {
            val date = LocalDate.of(2020, 1, 1)
            val viewModel = viewModelWithFailedDateLookup(date)
            stubSlowLookup(date, ZcashHeightResult.Success(1L))

            viewModel.onDoneClick()
            advanceTimeBy(100)
            viewModel.onModeSelect(RestoreHeightMode.NewWallet)
            advanceUntilIdle()

            assertNull(viewModel.uiState.closeWithResult)
            assertNull(viewModel.uiState.birthdayHeight)
        }
}

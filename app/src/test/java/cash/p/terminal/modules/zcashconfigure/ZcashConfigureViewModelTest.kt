package cash.p.terminal.modules.zcashconfigure

import cash.p.terminal.R
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.ZcashHeightResult
import cash.p.terminal.strings.helpers.Translator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}

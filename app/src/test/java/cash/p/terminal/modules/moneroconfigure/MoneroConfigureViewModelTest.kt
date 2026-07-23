package cash.p.terminal.modules.moneroconfigure

import cash.p.terminal.R
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.strings.helpers.Translator
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MoneroConfigureViewModelTest {

    private val validateMoneroHeightUseCase = mockk<ValidateMoneroHeightUseCase>()

    @Test
    fun onClosed_closeResultSet_clearsCloseResult() {
        every { validateMoneroHeightUseCase.getTodayHeight() } returns 3_000_000L
        val viewModel = MoneroConfigureViewModel(validateMoneroHeightUseCase)

        viewModel.onDoneClick()
        assertNotNull(viewModel.uiState.closeWithResult)

        viewModel.onClosed()

        assertNull(viewModel.uiState.closeWithResult)
    }

    @Test
    fun onDoneClick_restoreAsNew_usesTodayHeight() {
        every { validateMoneroHeightUseCase.getTodayHeight() } returns 3_000_000L
        val viewModel = MoneroConfigureViewModel(validateMoneroHeightUseCase)

        viewModel.setBirthdayHeight("1")
        viewModel.onDoneClick()

        assertEquals("3000000", viewModel.uiState.closeWithResult?.birthdayHeight)
    }

    @Test
    fun onDatePicked_validDate_setsBirthdayHeight() {
        val date = LocalDate.of(2020, 1, 1)
        every { validateMoneroHeightUseCase.getHeight(date) } returns 654_321L
        val viewModel = MoneroConfigureViewModel(validateMoneroHeightUseCase)

        viewModel.onDatePicked(date)

        assertEquals("654321", viewModel.uiState.birthdayHeight)
        assertNull(viewModel.uiState.errorHeight)
    }

    @Test
    fun onDatePicked_useCaseReturnsInvalidHeight_setsInvalidHeightFormatError() {
        val date = LocalDate.of(2020, 1, 1)
        every { validateMoneroHeightUseCase.getHeight(date) } returns -1L
        val viewModel = MoneroConfigureViewModel(validateMoneroHeightUseCase)

        viewModel.onDatePicked(date)

        assertEquals(
            Translator.getString(R.string.invalid_height_format),
            viewModel.uiState.errorHeight
        )
    }
}

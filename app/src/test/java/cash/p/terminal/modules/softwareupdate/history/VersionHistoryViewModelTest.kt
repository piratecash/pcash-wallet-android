package cash.p.terminal.modules.softwareupdate.history

import cash.p.terminal.modules.softwareupdate.domain.GetVersionHistoryUseCase
import cash.p.terminal.modules.softwareupdate.domain.VersionHistory
import cash.p.terminal.network.github.domain.entity.AppRelease
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VersionHistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val getVersionHistoryUseCase = mockk<GetVersionHistoryUseCase>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun init_loadsCurrentAndOldMinors() = runTest(dispatcher) {
        coEvery { getVersionHistoryUseCase() } returns VersionHistory(
            current = release("0.57.2", "0.57"),
            currentIsActiveBranch = false,
            oldMinors = listOf("0.56", "0.55"),
        )

        val viewModel = VersionHistoryViewModel(getVersionHistoryUseCase)
        advanceUntilIdle()

        assertEquals("0.57.2", viewModel.uiState.current?.version)
        assertEquals(listOf("0.56", "0.55"), viewModel.uiState.oldMinors)
        assertFalse(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.error)
    }

    @Test
    fun init_failure_setsErrorState() = runTest(dispatcher) {
        coEvery { getVersionHistoryUseCase() } throws RuntimeException("network")

        val viewModel = VersionHistoryViewModel(getVersionHistoryUseCase)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.error)
        assertFalse(viewModel.uiState.loading)
    }

    private fun release(version: String, minor: String) = AppRelease(
        version = version,
        minor = minor,
        tagName = "v$version-fdroid",
        publishedAt = Instant.EPOCH,
        htmlUrl = "https://example",
        apkSizeBytes = 1L,
        apkDownloadUrl = "https://example.apk",
    )
}

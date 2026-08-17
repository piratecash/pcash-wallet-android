package cash.p.terminal.core.managers

import cash.p.terminal.core.usecase.OfflineModeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

class AccountStorageCleanerTest {

    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)

    private val cleaner = AccountStorageCleaner(locallyCreatedTransactionRepository, offlineModeUseCase)

    @Test
    fun clearAccounts_bothCleanersSucceed_clearsEveryTable() = runTest {
        cleaner.clearAccounts(ACCOUNT_IDS)

        coVerify(exactly = 1) { locallyCreatedTransactionRepository.deleteByAccountIds(ACCOUNT_IDS) }
        coVerify(exactly = 1) { offlineModeUseCase.forgetAccounts(ACCOUNT_IDS) }
    }

    @Test
    fun clearAccounts_firstCleanerFails_stillClearsOfflineRows() = runTest {
        coEvery { locallyCreatedTransactionRepository.deleteByAccountIds(any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: IOException) {
            thrown = e
        }

        coVerify(exactly = 1) { offlineModeUseCase.forgetAccounts(ACCOUNT_IDS) }
        assertNotNull(thrown)
    }

    /** Swallowing this would let the caller drop the deleted-account records that drive the retry. */
    @Test
    fun clearAccounts_offlineRowsFail_propagatesFailure() = runTest {
        coEvery { offlineModeUseCase.forgetAccounts(any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull(thrown)
    }

    @Test
    fun clearAccounts_cleanerCancelled_propagatesCancellation() = runTest {
        coEvery { locallyCreatedTransactionRepository.deleteByAccountIds(any()) } throws CancellationException("stop")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertNotNull(thrown)
        coVerify(exactly = 0) { offlineModeUseCase.forgetAccounts(any()) }
    }

    private companion object {
        val ACCOUNT_IDS = listOf("acc-a", "acc-b")
    }
}

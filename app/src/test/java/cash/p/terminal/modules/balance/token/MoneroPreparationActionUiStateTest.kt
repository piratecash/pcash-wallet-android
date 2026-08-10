package cash.p.terminal.modules.balance.token

import cash.p.terminal.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroPreparationActionUiStateTest {

    @Test
    fun actionUiState_completedWithoutError_exposesRetryWithoutLoading() {
        val state = moneroPreparationActionUiState(
            syncInProgress = false,
        )

        assertEquals(R.string.Button_Retry, state.title)
        assertTrue(state.enabled)
        assertFalse(state.loading)
    }
}

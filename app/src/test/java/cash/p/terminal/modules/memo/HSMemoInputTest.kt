package cash.p.terminal.modules.memo
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import cash.p.terminal.modules.address.MemoPrefill
import cash.p.terminal.modules.address.MemoUnique
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HSMemoInputTest {
    @get:Rule val composeTestRule = createComposeRule()
    @Test fun externalMemo_eventsApplyAcknowledgeAndDoNotReplay() {
        val values = mutableListOf<String>(); val handledIds = mutableListOf<Long>()
        val stateRestorationTester = StateRestorationTester(composeTestRule)
        var event by mutableStateOf<MemoUnique?>(null); var memo by mutableStateOf("")
        stateRestorationTester.setContent {
            ComposeAppTheme {
                var navigationEvent by remember { mutableStateOf<MemoUnique?>(MemoUnique("memo", 1, true)) }
                HSMemoInput(
                    maxLength = 4,
                    initial = memo,
                    memoPrefill = MemoPrefill(event ?: navigationEvent) { id ->
                        handledIds += id
                        if (event?.id == id) event = null else navigationEvent = null
                    },
                    onValueChange = { memo = it; values += it },
                )
            }
        }
        composeTestRule.onNode(hasSetTextAction()).assertTextEquals("memo")
        assertEquals(listOf("memo"), values); assertEquals(listOf(1L), handledIds)
        composeTestRule.runOnIdle { event = MemoUnique("too long", 2) }
        composeTestRule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(listOf("memo", ""), values)
        assertEquals(listOf(1L, 2L), handledIds); composeTestRule.onNode(hasSetTextAction()).performTextReplacement("user")
        composeTestRule.runOnIdle { memo = "" }; stateRestorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.onNode(hasSetTextAction()).assertTextEquals("user"); assertEquals("user", memo)
        assertEquals(listOf("memo", "", "user", "user"), values)
    }
    @Test fun navigationMemo_explicitlyInitializedEmpty_doesNotReplay() {
        val values = mutableListOf<String>(); val handledIds = mutableListOf<Long>()
        var visible by mutableStateOf(true)
        val stateRestorationTester = StateRestorationTester(composeTestRule)
        stateRestorationTester.setContent {
            ComposeAppTheme {
                var event by remember { mutableStateOf<MemoUnique?>(MemoUnique("stale", 1, true)) }
                HSMemoInput(
                    maxLength = 20,
                    memoPrefill = MemoPrefill(event) { handledIds += it; event = null },
                    onValueChange = values::add,
                    visible = visible,
                )
            }
        }
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("")
        composeTestRule.runOnIdle { visible = false }; stateRestorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(listOf("stale", "", ""), values); assertEquals(listOf(1L, 1L), handledIds)
    }
    @Test fun externalMemo_separatePrefillLimit_preservesLongPrefillAsReadOnly() {
        val values = mutableListOf<String>()
        var event by mutableStateOf<MemoUnique?>(MemoUnique("long memo", 1))
        composeTestRule.setContent {
            ComposeAppTheme {
                HSMemoInput(
                    maxLength = 4,
                    memoPrefill = MemoPrefill(event) { event = null },
                    onValueChange = values::add,
                    prefillMaxLength = 20,
                )
            }
        }

        composeTestRule.onNodeWithText("long memo").assertIsNotEnabled()
        assertEquals(listOf("long memo"), values)
    }
    @Test fun externalMemo_overByteLimit_clearsPrefill() {
        val values = mutableListOf<String>()
        var event by mutableStateOf<MemoUnique?>(MemoUnique("🙂🙂🙂", 1))
        composeTestRule.setContent {
            ComposeAppTheme {
                HSMemoInput(
                    maxLength = 4,
                    memoPrefill = MemoPrefill(event) { event = null },
                    onValueChange = values::add,
                    prefillMaxLength = 20,
                    prefillMaxBytes = 9,
                )
            }
        }

        composeTestRule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        assertEquals(listOf(""), values)
    }
}

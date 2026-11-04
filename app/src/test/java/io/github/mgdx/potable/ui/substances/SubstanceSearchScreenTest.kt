package io.github.mgdx.potable.ui.substances

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.SubstanceMatch
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** "Search for a substance" (SPEC §3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubstanceSearchScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val repository = FakeSubstanceSearchRepository()

    private fun show(
        restoration: StateRestorationTester? = null,
        onOpenHistory: (SubstanceMatch) -> Unit = {},
    ) {
        val viewModel = SubstanceSearchViewModel(
            repository,
            FakeAppPreferences(),
            "45234",
            "045000474",
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC),
            SavedStateHandle(),
        )
        val content = @Composable {
            PotableTheme {
                SubstanceSearchScreen(
                    viewModel = viewModel,
                    communeName = "Orléans",
                    onBack = {},
                    onOpenHistory = onOpenHistory,
                )
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    private fun search(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNode(hasSetTextAction()).performImeAction()
    }

    @Test
    fun `a result shows its value as published and opens its history`() {
        repository.answers["atrazine"] = Result.success(
            result(
                match("1107", "Atrazine", "<0,02"),
                match("1830", "Atrazine déséthyl", "0,03", count = 12),
            ),
        )
        val opened = mutableListOf<SubstanceMatch>()
        show { opened += it }

        search("atrazine")
        compose.waitForText("Atrazine déséthyl")

        compose.onNode(hasText("<0,02 µg/L", substring = true)).assertExists()
        compose.onNode(hasClickAction() and hasText("Atrazine déséthyl")).performClick()
        assertEquals(listOf("1830"), opened.map { it.parameterCode })
    }

    @Test
    fun `nothing found says the substance may have another name`() {
        repository.answers["chlordecone"] = Result.success(result())
        show()

        search("chlordecone")

        compose.waitForText(string(R.string.substance_search_other_name))
    }

    @Test
    fun `the field has the focus when the screen opens`() {
        show()

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `the focus is not given again once the screen comes back`() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        // The search key closes the keyboard and leaves the field.
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `clearing the field puts the focus back in it`() {
        show()
        // Typed, then searched: the search key leaves the field.
        search("atrazine")
        compose.onNode(hasSetTextAction()).assertIsNotFocused()

        compose.onNode(hasContentDescription(string(R.string.action_clear_search)))
            .performClick()

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }
}

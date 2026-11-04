package io.github.mgdx.potable.ui.substances

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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

    private fun show(onOpenHistory: (SubstanceMatch) -> Unit = {}) {
        val viewModel = SubstanceSearchViewModel(
            repository,
            FakeAppPreferences(),
            "45234",
            "045000474",
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC),
            SavedStateHandle(),
        )
        compose.setContent {
            PotableTheme {
                SubstanceSearchScreen(
                    viewModel = viewModel,
                    communeName = "Orléans",
                    onBack = {},
                    onOpenHistory = onOpenHistory,
                )
            }
        }
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
}

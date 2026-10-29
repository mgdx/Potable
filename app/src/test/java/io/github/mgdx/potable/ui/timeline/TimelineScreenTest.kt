package io.github.mgdx.potable.ui.timeline

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeHistoryRepository
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

/** The timeline of a unit's samples (SPEC §5 F5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun summary(code: String, date: String, conclusion: String) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = conclusion,
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = Compliance.COMPLIANT,
    )

    @Test
    fun `touching a sample opens the report of that sample`() {
        val history = FakeHistoryRepository().apply {
            timeline = Result.success(
                listOf(
                    summary("00456", "2026-09-15T08:00:00Z", "Conclusion du 15 septembre."),
                    summary("00123", "2026-06-02T08:00:00Z", "Conclusion du 2 juin."),
                ),
            )
        }
        val viewModel = TimelineViewModel(
            history,
            FakeAppPreferences(),
            "45234",
            "045000123",
            Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC),
            SavedStateHandle(),
        )
        val opened = mutableListOf<String>()
        compose.setContent {
            PotableTheme {
                TimelineScreen(
                    viewModel = viewModel,
                    communeName = "Orléans",
                    onBack = {},
                    onOpenSample = { opened += it },
                )
            }
        }
        compose.waitForText("Conclusion du 2 juin.")

        compose.onNode(hasText("Conclusion du 2 juin.") and hasClickAction()).performClick()

        assertEquals(listOf("00123"), opened)
    }
}

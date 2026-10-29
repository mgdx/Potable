package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewAnalysisTest {
    private val earlier = Instant.parse("2026-08-12T10:54:00Z")
    private val later = Instant.parse("2026-08-26T12:50:00Z")

    private fun summary(
        code: String,
        date: String,
        physicochemicalLimits: Compliance = Compliance.COMPLIANT,
    ) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
            "pour l'ensemble des paramètres mesurés.",
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = physicochemicalLimits,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = Compliance.COMPLIANT,
    )

    private fun codes(samples: List<SampleSummary>) = samples.map { it.code }

    @Test
    fun `the first check is a baseline, not a new analysis`() {
        val candidates = listOf(summary("P1", "2026-08-26T12:50:00Z"))
        assertEquals(emptyList<SampleSummary>(), NewAnalysis.newSamples(null, null, candidates))
        assertEquals(
            emptyList<SampleSummary>(),
            NewAnalysis.newSamples(null, emptySet(), candidates),
        )
    }

    @Test
    fun `no candidate is nothing new`() {
        assertEquals(emptyList<SampleSummary>(), NewAnalysis.newSamples(earlier, null, emptyList()))
        assertEquals(
            emptyList<SampleSummary>(),
            NewAnalysis.newSamples(earlier, setOf("P1"), emptyList()),
        )
    }

    @Test
    fun `the samples already seen are not new`() {
        val candidates =
            listOf(summary("P1", "2026-08-26T12:50:00Z"), summary("P0", "2026-08-12T10:54:00Z"))
        assertEquals(
            emptyList<SampleSummary>(),
            NewAnalysis.newSamples(later, setOf("P0", "P1"), candidates),
        )
    }

    @Test
    fun `two samples of the same day are both judged, the unfavourable one included`() {
        // Vatry (51595), 2026-08-31: the newest row is the compliant sample.
        val compliant = summary("05100146767", "2026-08-31T14:05:00Z")
        val pesticides =
            summary(
                "05100146860",
                "2026-08-31T14:04:00Z",
                physicochemicalLimits = Compliance.NON_COMPLIANT,
            )
        val seen = setOf("05100146000")

        val new = NewAnalysis.newSamples(later, seen, listOf(compliant, pesticides))

        assertEquals(listOf("05100146767", "05100146860"), codes(new))
        assertTrue(new.any(Unfavourable::isUnfavourable))
        // One of them seen at the previous check: the other is still new.
        assertEquals(
            listOf("05100146860"),
            codes(
                NewAnalysis.newSamples(
                    compliant.date,
                    setOf("05100146767"),
                    listOf(compliant, pesticides),
                ),
            ),
        )
    }

    @Test
    fun `a sample published late, dated before the last one seen, is new`() {
        val seenOne = summary("P2", "2026-08-26T12:50:00Z")
        val late = summary("P1", "2026-08-20T09:00:00Z")

        assertEquals(
            listOf("P1"),
            codes(NewAnalysis.newSamples(later, setOf("P2"), listOf(seenOne, late))),
        )
    }

    @Test
    fun `several new samples come newest first`() {
        val candidates =
            listOf(
                summary("P3", "2026-09-01T08:00:00Z"),
                summary("P5", "2026-09-20T08:00:00Z"),
                summary("P1", "2026-08-26T12:50:00Z"),
                summary("P4", "2026-09-20T08:00:00Z"),
                // The latest row, asked for apart, repeats a sample of the window.
                summary("P5", "2026-09-20T08:00:00Z"),
            )

        assertEquals(
            listOf("P4", "P5", "P3"),
            codes(NewAnalysis.newSamples(later, setOf("P1"), candidates)),
        )
    }

    @Test
    fun `an empty set of seen codes makes every candidate new`() {
        val candidates =
            listOf(summary("P1", "2026-08-26T12:50:00Z"), summary("P0", "2026-08-12T10:54:00Z"))
        assertEquals(
            listOf("P1", "P0"),
            codes(NewAnalysis.newSamples(later, emptySet(), candidates)),
        )
    }

    @Test
    fun `a unit followed before the codes were kept falls back to the date`() {
        val candidates =
            listOf(
                summary("P2", "2026-09-15T09:00:00Z"),
                summary("P1", "2026-08-26T12:50:00Z"),
                summary("P0", "2026-08-12T10:54:00Z"),
            )

        // After an update: the window is not announced, only what is newer.
        assertEquals(listOf("P2"), codes(NewAnalysis.newSamples(later, null, candidates)))
        assertEquals(
            emptyList<String>(),
            codes(NewAnalysis.newSamples(candidates.first().date, null, candidates)),
        )
    }

    @Test
    fun `the newest sample of a real page is detected`() {
        val rows = HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234.json")).data
        val summaries = Series.sampleSummaries(rows)
        val newest = summaries.first()

        assertEquals(newest.code, NewAnalysis.newSamples(earlier, null, summaries).first().code)
        val allButNewest = summaries.drop(1).mapTo(HashSet()) { it.code }
        assertEquals(
            listOf(newest.code),
            codes(NewAnalysis.newSamples(earlier, allButNewest, summaries)),
        )
        assertFalse(NewAnalysis.newSamples(newest.date, null, summaries).isNotEmpty())
    }
}

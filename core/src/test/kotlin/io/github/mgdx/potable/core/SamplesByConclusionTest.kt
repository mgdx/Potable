package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SamplesByConclusionTest {
    private val compliant = "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
        "pour l'ensemble des paramètres mesurés."

    @Test
    fun `identical conclusions make one group, in the order of the samples`() {
        val samples = listOf(
            sample("D", compliant),
            sample("C", compliant),
            sample("B", compliant),
            sample("A", compliant),
        )
        assertEquals(listOf(listOf("D", "C", "B", "A")), codes(Samples.byConclusion(samples)))
    }

    @Test
    fun `only leading and trailing spaces are ignored`() {
        val samples = listOf(sample("B", "  $compliant\n"), sample("A", compliant))
        assertEquals(listOf(listOf("B", "A")), codes(Samples.byConclusion(samples)))
    }

    @Test
    fun `conclusions one character or one word apart stay apart`() {
        val samples = listOf(
            sample("D", compliant),
            sample("C", compliant.lowercase()),
            sample("B", compliant.replace("en vigueur ", "")),
            sample("A", compliant.replace("  ", " ").replace("qualité", "qualité  ")),
        )
        assertEquals(
            listOf(listOf("D"), listOf("C"), listOf("B"), listOf("A")),
            codes(Samples.byConclusion(samples)),
        )
    }

    @Test
    fun `groups come in the order of their newest sample`() {
        val other = "Eau non conforme aux limites de qualité."
        val samples = listOf(
            sample("D", other),
            sample("C", compliant),
            sample("B", other),
            sample("A", compliant),
        )
        assertEquals(
            listOf(listOf("D", "B"), listOf("C", "A")),
            codes(Samples.byConclusion(samples)),
        )
    }

    @Test
    fun `samples without a conclusion are never grouped`() {
        val samples = listOf(
            sample("D", null),
            sample("C", null),
            sample("B", "  "),
            sample("A", ""),
        )
        assertEquals(
            listOf(listOf("D"), listOf("C"), listOf("B"), listOf("A")),
            codes(Samples.byConclusion(samples)),
        )
        assertTrue(Samples.byConclusion(emptyList()).isEmpty())
    }

    private fun codes(groups: List<List<Sample>>) = groups.map { group -> group.map { it.code } }

    private fun sample(code: String, conclusion: String?) = Sample(
        code = code,
        communeCode = "75056",
        networkCode = "075000225",
        date = Instant.parse("2026-06-30T12:00:00Z"),
        conclusion = conclusion,
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = Compliance.COMPLIANT,
        distributor = null,
        owner = null,
        results = emptyList(),
    )
}

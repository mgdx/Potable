package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UnfavourableDigestTest {
    private fun summary(
        code: String,
        date: String,
        conclusion: String = "Eau d'alimentation conforme aux exigences de qualité en vigueur.",
        bacteriologicalLimits: Compliance = Compliance.COMPLIANT,
        physicochemicalLimits: Compliance = Compliance.COMPLIANT,
        physicochemicalReferences: Compliance = Compliance.COMPLIANT,
    ) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = conclusion,
        bacteriologicalLimits = bacteriologicalLimits,
        physicochemicalLimits = physicochemicalLimits,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = physicochemicalReferences,
    )

    private val advisedAgainst = "Consommation déconseillée aux nourrissons."

    /** Orléans, 2026: compliant with the limits, a reference exceeded (temperature). */
    private val temperature =
        "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble " +
            "des paramètres mesurés, à l'exception de la température excessive."

    @Test
    fun `nothing unfavourable gives no digest`() {
        assertNull(UnfavourableDigest.of(emptyList()))
        assertNull(
            UnfavourableDigest.of(
                listOf(summary("A", "2026-08-26T12:50:00Z"), summary("B", "2026-03-01T09:00:00Z")),
            ),
        )
    }

    @Test
    fun `limits, references only and conclusion only are counted apart`() {
        val bacteria =
            summary(
                "BACT",
                "2026-01-10T09:00:00Z",
                bacteriologicalLimits = Compliance.NON_COMPLIANT,
            )
        // Limits and references both: counted with the limits.
        val both = summary(
            "BOTH",
            "2026-02-10T09:00:00Z",
            physicochemicalLimits = Compliance.NON_COMPLIANT,
            physicochemicalReferences = Compliance.NON_COMPLIANT,
        )
        val reference = summary(
            "REF",
            "2026-06-02T10:00:00Z",
            conclusion = temperature,
            physicochemicalReferences = Compliance.NON_COMPLIANT,
        )
        val advised = summary("ADV", "2026-04-02T10:00:00Z", conclusion = advisedAgainst)
        val digest = UnfavourableDigest.of(
            listOf(
                summary("OK", "2026-08-26T12:50:00Z"),
                bacteria,
                both,
                reference,
                advised,
                bacteria,
            ),
        )!!

        assertEquals(2, digest.limitsCount)
        assertEquals(1, digest.referencesOnlyCount)
        assertEquals(1, digest.conclusionOnlyCount)
        assertEquals(4, digest.count)
        // The most recent, its conclusion as published.
        assertEquals(reference, digest.latest)
        assertEquals(temperature, digest.latest.conclusion)
    }

    @Test
    fun `between samples of the same date, the first by code is the most recent`() {
        val a = summary("A", "2026-06-02T10:00:00Z", conclusion = advisedAgainst)
        val b = summary("B", "2026-06-02T10:00:00Z", conclusion = advisedAgainst)

        assertEquals("A", UnfavourableDigest.of(listOf(b, a))!!.latest.code)
    }
}

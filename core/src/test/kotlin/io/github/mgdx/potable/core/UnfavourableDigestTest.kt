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
    ) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = conclusion,
        bacteriologicalLimits = bacteriologicalLimits,
        physicochemicalLimits = Compliance.COMPLIANT,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = Compliance.COMPLIANT,
    )

    private val advisedAgainst =
        "Eau non conforme aux limites de qualité. Consommation déconseillée aux nourrissons."

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
    fun `the unfavourable samples are counted, the most recent kept as published`() {
        val older =
            summary("OLD", "2026-01-10T09:00:00Z", bacteriologicalLimits = Compliance.NON_COMPLIANT)
        val recent = summary("RECENT", "2026-06-02T10:00:00Z", conclusion = advisedAgainst)
        val digest = UnfavourableDigest.of(
            listOf(summary("OK", "2026-08-26T12:50:00Z"), older, recent, older),
        )

        assertEquals(UnfavourableDigest(count = 2, latest = recent), digest)
        assertEquals(advisedAgainst, digest!!.latest.conclusion)
    }

    @Test
    fun `between samples of the same date, the first by code is the most recent`() {
        val a = summary("A", "2026-06-02T10:00:00Z", conclusion = advisedAgainst)
        val b = summary("B", "2026-06-02T10:00:00Z", conclusion = advisedAgainst)

        assertEquals("A", UnfavourableDigest.of(listOf(b, a))!!.latest.code)
    }
}

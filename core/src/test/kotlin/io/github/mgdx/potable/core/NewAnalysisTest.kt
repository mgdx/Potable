package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewAnalysisTest {
    private val earlier = Instant.parse("2026-08-12T10:54:00Z")
    private val later = Instant.parse("2026-08-26T12:50:00Z")

    @Test
    fun `a newer sample is new`() = assertTrue(NewAnalysis.isNew(earlier, later))

    @Test
    fun `the same sample is not new`() = assertFalse(NewAnalysis.isNew(later, later))

    @Test
    fun `an older sample is not new`() = assertFalse(NewAnalysis.isNew(later, earlier))

    @Test
    fun `the first pass is a baseline, not a new analysis`() =
        assertFalse(NewAnalysis.isNew(null, later))

    @Test
    fun `no sample is not new`() {
        assertFalse(NewAnalysis.isNew(earlier, null))
        assertFalse(NewAnalysis.isNew(null, null))
    }

    @Test
    fun `the newest sample of a real page is detected`() {
        val rows = HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234.json")).data
        val latest = Samples.group(rows).first().date
        assertTrue(NewAnalysis.isNew(earlier, latest))
        assertFalse(NewAnalysis.isNew(latest, latest))
    }
}

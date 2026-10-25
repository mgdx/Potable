package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Threshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SeriesTest {
    private fun row(
        sample: String,
        date: String?,
        code: String? = "1340",
        text: String? = "4,40",
        value: Double? = 4.4,
        label: String? = "Nitrates (en NO3)",
        unit: String? = "mg/L",
        limit: String? = "<=50 mg/L",
        reference: String? = null,
        place: String? = null,
    ) = ResultRow(
        codePrelevement = sample,
        codeCommune = "45234",
        datePrelevement = date,
        codeParametre = code,
        libelleParametre = label,
        resultatAlphanumerique = text,
        resultatNumerique = value,
        libelleUnite = unit,
        limiteQualiteParametre = limit,
        referenceQualiteParametre = reference,
        codeLieuAnalyse = place,
    )

    @Test
    fun `points are deduplicated by sample and sorted oldest first`() {
        val series = Series.parameterSeries(
            listOf(
                row("P3", "2026-08-26T12:50:00Z"),
                row("P1", "2024-01-10T09:00:00Z", text = "3,1", value = 3.1),
                row("P3", "2026-08-26T12:50:00Z", text = "9,99", value = 9.99),
                row("P2", "2025-05-02T10:00:00Z", text = "5,0", value = 5.0),
            ),
            setOf("1340"),
        )!!
        assertEquals(listOf("P1", "P2", "P3"), series.points.map { it.sampleCode })
        assertEquals(listOf(3.1, 5.0, 4.4), series.points.map { it.value })
        assertEquals(Instant.parse("2024-01-10T09:00:00Z"), series.points.first().date)
        assertEquals(Threshold(null, 50.0, "mg/L"), series.limit)
        assertNull(series.reference)
    }

    @Test
    fun `labels, unit and bounds come from the newest row`() {
        val series = Series.parameterSeries(
            listOf(
                row(
                    "NEW",
                    "2026-01-01T00:00:00Z",
                    code = "7972",
                    label = "TH",
                    unit = "°f",
                    limit = null,
                ),
                row(
                    "OLD",
                    "2020-01-01T00:00:00Z",
                    code = "1345",
                    label = "Old",
                    unit = "x",
                    limit = "<=1 x",
                ),
            ),
            setOf("1345", "7972"),
        )!!
        assertEquals("7972", series.parameterCode)
        assertEquals("TH", series.label)
        assertEquals("°f", series.unit)
        assertNull(series.limitText)
        assertNull(series.limit)
        assertEquals(2, series.points.size)
    }

    @Test
    fun `Paris anthraquinone keeps the limit published on one of the sample's two rows`() {
        // Real page (2026-10-25): each sample may publish the parameter twice
        // under the same code, "(HAP)" without limit and "(pesticide)" with
        // one, in either order; the newest sample lists the HAP row first.
        val rows = HubEauParser
            .parseResultats(Fixtures.hubeau("resultats_dis_75056_anthraquinone.json"))
            .data
        assertEquals(12, rows.size)
        val series = Series.parameterSeries(rows, setOf("2013"))!!
        assertEquals(7, series.points.size)
        assertEquals("<=0,1 µg/L", series.limitText)
        assertEquals(Threshold(null, 0.1, "µg/L"), series.limit)
        assertEquals("Anthraquinone (pesticide)", series.label)

        // A window holding that sample alone: a single measure, same limit.
        val single = Series.parameterSeries(rows.take(2), setOf("2013"))!!
        assertEquals(1, single.points.size)
        assertEquals("<=0,1 µg/L", single.limitText)
    }

    @Test
    fun `thresholds published on another row of the newest sample are kept`() {
        val series = Series.parameterSeries(
            listOf(
                row("P1", "2026-01-01T00:00:00Z", limit = null, place = "L"),
                row(
                    "P1",
                    "2026-01-01T00:00:00Z",
                    limit = "<=50 mg/L",
                    reference = "<=25 mg/L",
                    place = "T",
                    value = 9.0,
                ),
            ),
            setOf("1340"),
        )!!
        // The laboratory value is kept, the thresholds of the on-site row too.
        assertEquals(listOf(4.4), series.points.map { it.value })
        assertEquals("<=50 mg/L", series.limitText)
        assertEquals("<=25 mg/L", series.referenceText)
    }

    @Test
    fun `the label falls back on the code`() {
        val series = Series.parameterSeries(
            listOf(row("P1", "2026-01-01T00:00:00Z", label = null)),
            setOf("1340"),
        )!!
        assertEquals("1340", series.label)
    }

    @Test
    fun `other parameters and rows without a code are ignored`() {
        val rows = listOf(
            row("P1", "2026-01-01T00:00:00Z", code = "1302"),
            row("P2", "2026-01-01T00:00:00Z", code = null),
        )
        assertNull(Series.parameterSeries(rows, setOf("1340")))
        assertNull(Series.parameterSeries(emptyList(), setOf("1340")))
    }

    @Test
    fun `censored values are those published as a bound`() {
        assertTrue(Series.isCensored("<0,01"))
        assertTrue(Series.isCensored(" >80"))
        assertTrue(Series.isCensored("≤1"))
        assertTrue(Series.isCensored("≥2"))
        assertFalse(Series.isCensored("4,40"))
        assertFalse(Series.isCensored("N.M."))
        assertFalse(Series.isCensored(""))
        assertFalse(Series.isCensored(null))

        val series = Series.parameterSeries(
            listOf(
                row("P1", "2026-01-01T00:00:00Z", text = "<1", value = 0.0),
                row("P2", "2026-02-01T00:00:00Z", text = "N.M.", value = null),
            ),
            setOf("1340"),
        )!!
        assertEquals(listOf(true, false), series.points.map { it.censored })
        assertEquals(listOf("<1", "N.M."), series.points.map { it.text })
        assertEquals(listOf(0.0, null), series.points.map { it.value })
    }

    @Test
    fun `Paris pH measured on site and in the laboratory keeps the laboratory value`() {
        // Real page: 8 rows, 5 samples, 3 of them with a "T" and an "L" pH.
        val rows = HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_75056_ph.json")).data
        assertEquals(8, rows.size)
        val series = Series.parameterSeries(rows, setOf("1302"))!!
        assertEquals(
            listOf("07500217020", "07500217015", "07500217014", "07500216806", "07500216807"),
            series.points.map { it.sampleCode },
        )
        assertEquals(listOf("7,66", "7,63", "7,58", "7,6", "7,7"), series.points.map { it.text })
        assertEquals("pH", series.label)
        assertEquals("unité pH", series.unit)
        assertNull(series.limit)
        assertEquals(">=6,5 et <=9 unité pH", series.referenceText)
        assertEquals(Threshold(6.5, 9.0, "unité pH"), series.reference)
    }

    @Test
    fun `Orleans pH draws its reference range, nitrates their limit`() {
        // Real pages (2026-10-25): device testing showed the pH reference "not drawn".
        val ph = Series.parameterSeries(
            HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234_ph.json")).data,
            setOf("1302"),
        )!!
        assertEquals(10, ph.points.size)
        assertNull(ph.limitText)
        assertNull(ph.limit)
        assertEquals(">=6,5 et <=9 unité pH", ph.referenceText)
        assertEquals(Threshold(6.5, 9.0, "unité pH"), ph.reference)

        val nitrates = Series.parameterSeries(
            HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234_nitrates.json")).data,
            setOf("1340"),
        )!!
        assertEquals(10, nitrates.points.size)
        assertEquals(Threshold(null, 50.0, "mg/L"), nitrates.limit)
        assertNull(nitrates.reference)
    }

    @Test(expected = HubEauFormatException::class)
    fun `a kept row without a date is a format error`() {
        Series.parameterSeries(listOf(row("P1", null)), setOf("1340"))
    }

    @Test
    fun `the timeline has one summary per sample, newest first`() {
        // Real page: pH, E. coli and lead rows of 4 samples (8 rows).
        val rows =
            HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234_frise.json")).data
        assertEquals(8, rows.size)
        val timeline = Series.sampleSummaries(rows)
        assertEquals(4, timeline.size)
        assertEquals(timeline.sortedByDescending { it.date }, timeline)
        assertEquals(timeline.map { it.code }.distinct(), timeline.map { it.code })
        val newest = timeline.first()
        assertEquals("04500171467", newest.code)
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), newest.date)
        assertEquals(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
                "pour l'ensemble des paramètres mesurés.",
            newest.conclusion,
        )
        assertEquals(Compliance.COMPLIANT, newest.bacteriologicalLimits)
        assertEquals(Compliance.COMPLIANT, newest.physicochemicalReferences)
    }

    @Test
    fun `the timeline keeps the header of the first row of a sample`() {
        val rows = listOf(
            ResultRow(
                codePrelevement = "B",
                codeCommune = "45234",
                datePrelevement = "2026-01-01T00:00:00Z",
                conclusionConformitePrelevement = "Non conforme",
                conformiteLimitesBactPrelevement = "N",
            ),
            ResultRow(
                codePrelevement = "A",
                codeCommune = "45234",
                datePrelevement = "2026-02-01T00:00:00Z",
                conformiteLimitesBactPrelevement = "S",
            ),
            ResultRow(
                codePrelevement = "B",
                codeCommune = "45234",
                datePrelevement = "2026-01-01T00:00:00Z",
                conclusionConformitePrelevement = "Autre",
                conformiteLimitesBactPrelevement = "C",
            ),
        )
        val timeline = Series.sampleSummaries(rows)
        assertEquals(listOf("A", "B"), timeline.map { it.code })
        assertNull(timeline[0].conclusion)
        assertEquals(Compliance.NOT_ASSESSED, timeline[0].bacteriologicalLimits)
        assertEquals("Non conforme", timeline[1].conclusion)
        assertEquals(Compliance.NON_COMPLIANT, timeline[1].bacteriologicalLimits)
        assertEquals(Compliance.UNKNOWN, timeline[1].physicochemicalLimits)
    }
}

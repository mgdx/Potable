package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.ParameterSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WatchedSubstancesTest {
    private fun row(
        sample: String,
        date: String,
        code: String,
        text: String,
        label: String = "Somme de 20 substances perfluoroalkylées (PFAS)",
        limit: String? = "<=0,1 µg/L",
        place: String? = "L",
    ) = ResultRow(
        codePrelevement = sample,
        codeCommune = "45234",
        datePrelevement = date,
        codeParametre = code,
        libelleParametre = label,
        resultatAlphanumerique = text,
        resultatNumerique = text.replace(',', '.').toDoubleOrNull(),
        libelleUnite = "µg/L",
        limiteQualiteParametre = limit,
        codeLieuAnalyse = place,
    )

    private fun series(substance: WatchedSubstance, vararg rows: ResultRow): ParameterSeries? =
        Series.parameterSeries(rows.toList(), substance.codes)

    @Test
    fun `codes checked against the API map to their substance`() {
        assertEquals(WatchedSubstance.PFAS, WatchedSubstance.of("8847"))
        assertEquals(WatchedSubstance.TOTAL_PESTICIDES, WatchedSubstance.of("6276"))
        assertEquals(WatchedSubstance.TFA, WatchedSubstance.of("8858"))
        assertEquals(WatchedSubstance.VINYL_CHLORIDE, WatchedSubstance.of("1753"))
        assertEquals(WatchedSubstance.LEAD, WatchedSubstance.of("1382"))
        assertEquals(WatchedSubstance.ARSENIC, WatchedSubstance.of("1369"))
        assertEquals(WatchedSubstance.PERCHLORATE, WatchedSubstance.of("6219"))
        assertEquals(WatchedSubstance.CHLORDECONE, WatchedSubstance.of("1866"))
    }

    @Test
    fun `chlordecone is read overseas only, and said not analysed in the Antilles only`() {
        val mainland = WatchedSubstances.readFor("45234")
        assertEquals(WatchedSubstance.entries - WatchedSubstance.CHLORDECONE, mainland)
        assertEquals(WatchedSubstance.entries, WatchedSubstances.readFor("97209"))
        assertEquals(WatchedSubstance.entries, WatchedSubstances.readFor("97411"))
        assertTrue(WatchedSubstance.CHLORDECONE.isAbsenceShownIn("97209"))
        assertTrue(WatchedSubstance.CHLORDECONE.isAbsenceShownIn("97101"))
        assertFalse(WatchedSubstance.CHLORDECONE.isAbsenceShownIn("97411"))
        assertFalse(WatchedSubstance.CHLORDECONE.isAbsenceShownIn("45234"))
        // "97" alone is not a department: Corsica and the rest keep two characters.
        assertTrue(WatchedSubstance.TFA.isAbsenceShownIn("2A004"))
    }

    @Test
    fun `a substance read without any measure is not analysed, a failed one is unknown`() {
        val lead = series(
            WatchedSubstance.LEAD,
            row("P1", "2026-01-05T10:00:00Z", "1382", "2", label = "Plomb"),
        )
        val arsenic = row("P1", "2026-01-05T10:00:00Z", "1369", "1")
        val empty = series(WatchedSubstance.ARSENIC, arsenic)!!.copy(points = emptyList())
        val read = mapOf(
            WatchedSubstance.TFA to null,
            WatchedSubstance.LEAD to lead,
            WatchedSubstance.ARSENIC to empty,
            WatchedSubstance.CHLORDECONE to null,
            // PFAS missing: its read failed.
        )
        assertEquals(
            listOf(WatchedSubstance.TFA, WatchedSubstance.ARSENIC, WatchedSubstance.CHLORDECONE),
            WatchedSubstances.notAnalysed(read, "97209"),
        )
        assertEquals(
            listOf(WatchedSubstance.TFA, WatchedSubstance.ARSENIC),
            WatchedSubstances.notAnalysed(read, "97411"),
        )
    }

    @Test
    fun `neighbouring parameters are not watched`() {
        assertNull(WatchedSubstance.of("9268")) // Somme de 4 PFAS
        assertNull(WatchedSubstance.of("5347")) // PFOA alone
        assertNull(WatchedSubstance.of("1340")) // Nitrates stay a key parameter
    }

    @Test
    fun `a code belongs to one substance only, and none is a key parameter`() {
        val codes = WatchedSubstance.entries.flatMap { it.codes }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.none { FeaturedParameter.of(it) != null })
    }

    @Test
    fun `the latest measure of each substance is kept, however old, in table order`() {
        val latest = WatchedSubstances.latest(
            mapOf(
                WatchedSubstance.LEAD to series(
                    WatchedSubstance.LEAD,
                    row(
                        "P1",
                        "2019-12-18T09:00:00Z",
                        "1382",
                        "<1",
                        label = "Plomb",
                        limit = "<=10 µg/L",
                    ),
                ),
                WatchedSubstance.PFAS to series(
                    WatchedSubstance.PFAS,
                    row("P2", "2026-03-26T10:00:00Z", "8847", "0,012"),
                    row("P1", "2025-02-10T10:00:00Z", "8847", "0,020"),
                ),
            ),
        )
        val order = listOf(WatchedSubstance.PFAS, WatchedSubstance.LEAD)
        assertEquals(order, latest.map { it.substance })
        val pfas = latest.first()
        assertEquals("0,012", pfas.point.text)
        assertEquals(Instant.parse("2026-03-26T10:00:00Z"), pfas.point.date)
        assertEquals("<=0,1 µg/L", pfas.limitText)
        assertEquals("µg/L", pfas.unit)
        assertEquals("Somme de 20 substances perfluoroalkylées (PFAS)", pfas.label)
        assertEquals("<1", latest[1].point.text)
        assertEquals(Instant.parse("2019-12-18T09:00:00Z"), latest[1].point.date)
    }

    @Test
    fun `a substance published under several codes gives its newest measure across them`() {
        // No watched substance has two codes yet: the series of hardness
        // shows how a set of codes is read (FeaturedParameter.HARDNESS).
        val codes = setOf("7972", "1345")
        val merged = Series.parameterSeries(
            listOf(
                row("P1", "2026-01-05T10:00:00Z", "7972", "20"),
                row("P2", "2026-04-05T10:00:00Z", "1345", "22"),
            ),
            codes,
        )
        val latest = WatchedSubstances.latest(mapOf(WatchedSubstance.ARSENIC to merged))
        assertEquals("22", latest.single().point.text)
    }

    @Test
    fun `a substance without series or without point has no line`() {
        val arsenic = row("P1", "2026-01-05T10:00:00Z", "1369", "1")
        val empty = series(WatchedSubstance.ARSENIC, arsenic)!!.copy(points = emptyList())
        val latest = WatchedSubstances.latest(
            mapOf(
                WatchedSubstance.PFAS to null,
                WatchedSubstance.ARSENIC to empty,
                WatchedSubstance.LEAD to series(
                    WatchedSubstance.LEAD,
                    row("P1", "2026-01-05T10:00:00Z", "1382", "2", label = "Plomb"),
                ),
            ),
        )
        assertEquals(listOf(WatchedSubstance.LEAD), latest.map { it.substance })
    }

    @Test
    fun `no substance measured gives no measure at all`() {
        assertTrue(WatchedSubstances.latest(emptyMap()).isEmpty())
        val none = WatchedSubstance.entries.associateWith { null }
        assertTrue(WatchedSubstances.latest(none).isEmpty())
    }

    @Test
    fun `a laboratory measure beats an on-site one of the same sample, in either order`() {
        val onSite = row("P1", "2026-05-02T10:00:00Z", "1369", "3", label = "Arsenic", place = "T")
        val laboratory =
            row("P1", "2026-05-02T10:00:00Z", "1369", "2,5", label = "Arsenic", place = "L")
        for (rows in listOf(listOf(onSite, laboratory), listOf(laboratory, onSite))) {
            val latest = WatchedSubstances.latest(
                mapOf(WatchedSubstance.ARSENIC to Series.parameterSeries(rows, setOf("1369"))),
            )
            assertEquals("2,5", latest.single().point.text)
        }
    }

    @Test
    fun `a blank published limit is no limit`() {
        val latest = WatchedSubstances.latest(
            mapOf(
                WatchedSubstance.PERCHLORATE to series(
                    WatchedSubstance.PERCHLORATE,
                    row(
                        "P1",
                        "2026-03-26T10:00:00Z",
                        "6219",
                        "<4",
                        label = "Perchlorate",
                        limit = " ",
                    ),
                ),
            ),
        )
        assertNull(latest.single().limitText)
    }
}

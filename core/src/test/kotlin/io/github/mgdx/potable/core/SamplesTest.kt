package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.Compliance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SamplesTest {
    private val orleans =
        HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234.json")).data

    @Test
    fun `rows are grouped by sample, not counted as samples`() {
        val samples = Samples.group(orleans)
        assertEquals(100, orleans.size)
        assertEquals(4, samples.size)
        assertEquals(
            listOf("04500171467", "04500171468", "04500171327", "04500171315"),
            samples.map { it.code },
        )
        assertEquals(listOf(27, 27, 28, 18), samples.map { it.results.size })
        assertEquals(orleans.size, samples.sumOf { it.results.size })
    }

    @Test
    fun `the sample header comes from its rows`() {
        val sample = Samples.group(orleans).first()
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), sample.date)
        assertEquals("45234", sample.communeCode)
        assertEquals("045000474", sample.networkCode)
        assertEquals(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
                "pour l'ensemble des paramètres mesurés.",
            sample.conclusion,
        )
        assertEquals(Compliance.COMPLIANT, sample.bacteriologicalLimits)
        assertEquals(Compliance.COMPLIANT, sample.physicochemicalLimits)
        assertEquals(Compliance.COMPLIANT, sample.bacteriologicalReferences)
        assertEquals(Compliance.COMPLIANT, sample.physicochemicalReferences)
        assertEquals("SUEZ AQUALIGE", sample.distributor)
        assertEquals("ORLEANS METROPOLE", sample.owner)
        assertEquals(
            "ORLEANS-ST JEAN LE BLANC-ST PRYVÉ-ST DENIS EN VAL",
            sample.upstreamFacility,
        )
    }

    @Test
    fun `compliance codes are kept per sample`() {
        val sample = Samples.group(orleans).single { it.code == "04500171327" }
        assertEquals(Compliance.COMPLIANT, sample.physicochemicalLimits)
        assertEquals(Compliance.NON_COMPLIANT, sample.physicochemicalReferences)
    }

    @Test
    fun `results keep the API order and the raw values`() {
        val sample = Samples.group(orleans).first()
        val apiOrder = orleans.filter { it.codePrelevement == sample.code }.map { it.codeParametre }
        assertEquals(apiOrder, sample.results.map { it.parameterCode })

        val nitrites = sample.results.first()
        assertEquals("1339", nitrites.parameterCode)
        assertEquals("Nitrites (en NO2)", nitrites.parameterLabel)
        assertEquals("N", nitrites.parameterTypeCode)
        assertEquals("<0,01", nitrites.resultText)
        assertEquals(0.0, nitrites.resultNumber!!, 0.0)
        assertEquals("mg/L", nitrites.unit)
        assertEquals("<=0,5 mg/L", nitrites.qualityLimit)
        assertNull(nitrites.qualityReference)

        assertNull(sample.results.single { it.resultText == "N.M." }.resultNumber)
    }

    @Test
    fun `a sample without conclusion`() {
        val rows = HubEauParser
            .parseResultats(Fixtures.hubeau("resultats_dis_sans_conclusion.json")).data
        val sample = Samples.group(rows).single()
        assertNull(sample.conclusion)
        assertEquals(Compliance.UNKNOWN, sample.bacteriologicalLimits)
        assertEquals(Compliance.UNKNOWN, sample.physicochemicalLimits)
        assertEquals(Compliance.UNKNOWN, sample.bacteriologicalReferences)
        assertEquals(Compliance.UNKNOWN, sample.physicochemicalReferences)
        assertEquals(3, sample.results.size)
    }

    @Test
    fun `no row gives no sample`() {
        assertTrue(Samples.group(emptyList()).isEmpty())
    }

    @Test
    fun `newest first, then by code`() {
        val rows = listOf(
            row("B", "2026-01-01T00:00:00Z"),
            row("Z", "2026-03-01T00:00:00Z"),
            row("A", "2026-01-01T00:00:00Z"),
            row("Z", "2026-03-01T00:00:00Z", parameter = "2"),
        )
        assertEquals(listOf("Z", "A", "B"), Samples.group(rows).map { it.code })
    }

    @Test
    fun `interleaved rows are regrouped`() {
        val rows = listOf(
            row("A", "2026-01-01T00:00:00Z", parameter = "1"),
            row("B", "2026-02-01T00:00:00Z", parameter = "1"),
            row("A", "2026-01-01T00:00:00Z", parameter = "2"),
        )
        val samples = Samples.group(rows)
        assertEquals(2, samples.size)
        val a = samples.single { it.code == "A" }
        assertEquals(listOf("1", "2"), a.results.map { it.parameterCode })
    }

    @Test
    fun `an identical parameter row is kept once`() {
        val date = "2026-01-01T00:00:00Z"
        val rows = listOf(
            row("A", date, parameter = "1", text = "5", number = 5.0),
            row("A", date, parameter = "1", text = "5", number = 5.0),
            row("A", date, parameter = "1", text = "6", number = 6.0),
            row("A", date, parameter = "2", text = "5", number = 5.0),
        )
        val results = Samples.group(rows).single().results
        assertEquals(
            listOf("1" to "5", "1" to "6", "2" to "5"),
            results.map { it.parameterCode to it.resultText },
        )
    }

    @Test
    fun `the same value measured in the laboratory and on site is kept twice`() {
        val date = "2026-01-01T00:00:00Z"
        val rows = listOf(
            row("A", date, parameter = "1302", text = "7,6", number = 7.6, place = "T"),
            row("A", date, parameter = "1302", text = "7,6", number = 7.6, place = "L"),
            row("A", date, parameter = "1302", text = "7,6", number = 7.6, place = "L"),
        )
        val results = Samples.group(rows).single().results
        assertEquals(listOf("T", "L"), results.map { it.analysisPlace })
        assertEquals(listOf(true, false), results.map { it.isOnSite })
    }

    @Test
    fun `the network code prefers code_reseau, then the first of reseaux`() {
        val base = row("A", "2026-01-01T00:00:00Z")
        val both = base.copy(codeReseau = "R1", reseaux = listOf(ResultRow.Network("R2")))
        assertEquals("R1", Samples.group(listOf(both)).single().networkCode)
        val list = base.copy(reseaux = listOf(ResultRow.Network("R2"), ResultRow.Network("R3")))
        assertEquals("R2", Samples.group(listOf(list)).single().networkCode)
        assertNull(Samples.group(listOf(base)).single().networkCode)
    }

    @Test
    fun `a row without code_parametre is in the sample but not in its results`() {
        val toulouse = HubEauParser.parseResultats(
            Fixtures.hubeau("resultats_dis_31555_sans_code_parametre.json"),
        ).data
        val sample = Samples.group(toulouse).single()
        assertEquals("03100216050", sample.code)
        assertEquals(
            listOf("1398", "1302", "1309", "1399"),
            sample.results.map {
                it.parameterCode
            },
        )
        assertTrue(sample.results.none { it.parameterLabel == "Prélèvement sous acréditation" })
    }

    @Test
    fun `a row without code_parametre still gives the sample header`() {
        val header = row("P1", "2026-08-31T14:43:00Z", parameter = null)
            .copy(conclusionConformitePrelevement = "Eau conforme.", nomDistributeur = "SETOM")
        val sample = Samples.group(
            listOf(header, row("P1", "2026-08-31T14:43:00Z", "1398")),
        ).single()
        assertEquals(Instant.parse("2026-08-31T14:43:00Z"), sample.date)
        assertEquals("Eau conforme.", sample.conclusion)
        assertEquals("SETOM", sample.distributor)
        assertEquals(listOf("1398"), sample.results.map { it.parameterCode })
    }

    @Test
    fun `a missing label falls back on the parameter code`() {
        val rows = listOf(row("A", "2026-01-01T00:00:00Z", label = null))
        assertEquals("1", Samples.group(rows).single().results.single().parameterLabel)
    }

    @Test
    fun `the latest day keeps every sample of the newest published day`() {
        val rows = listOf(
            row("LATE", "2026-08-26T23:30:00Z"),
            row("MORNING", "2026-08-26T08:15:00Z"),
            row("NOON", "2026-08-26T12:50:00Z"),
            row("BEFORE", "2026-08-25T23:59:00Z"),
        )
        assertEquals(
            listOf("LATE", "NOON", "MORNING"),
            Samples.latestDay(Samples.group(rows)).map { it.code },
        )
        assertEquals(
            listOf("BEFORE"),
            Samples.latestDay(Samples.group(listOf(rows.last()))).map { it.code },
        )
        assertTrue(Samples.latestDay(emptyList()).isEmpty())
    }

    @Test
    fun `the published day is the date read in UTC`() {
        // 23:30 "Z" is French time: still the 26th, not the 27th in Paris.
        assertEquals(
            LocalDate.parse("2026-08-26"),
            Samples.publishedDay(Instant.parse("2026-08-26T23:30:00Z")),
        )
        assertEquals(
            LocalDate.parse("2026-08-26"),
            Samples.publishedDayOrNull(row("A", "2026-08-26")),
        )
        assertNull(Samples.publishedDayOrNull(row("A", null)))
        assertNull(Samples.publishedDayOrNull(row("A", "26/08/2026")))
    }

    @Test
    fun `other ISO-8601 forms are read`() {
        assertEquals(
            Instant.parse("2026-08-26T10:50:00Z"),
            Samples.group(listOf(row("A", "2026-08-26T12:50:00+02:00"))).single().date,
        )
        assertEquals(
            Instant.parse("2026-08-26T00:00:00Z"),
            Samples.group(listOf(row("A", "2026-08-26"))).single().date,
        )
    }

    @Test
    fun `an unreadable date throws a format exception`() {
        for (date in listOf(null, "", "26/08/2026", "2026-13-01T00:00:00Z")) {
            val rows = listOf(row("A", "2026-01-01T00:00:00Z"), row("A", date, parameter = "2"))
            try {
                Samples.group(rows)
                fail("expected HubEauFormatException for date $date")
            } catch (_: HubEauFormatException) {
                // expected
            }
        }
    }

    private fun row(
        sample: String,
        date: String?,
        parameter: String? = "1",
        label: String? = "Paramètre $parameter",
        text: String? = null,
        number: Double? = null,
        place: String? = null,
    ) = ResultRow(
        codePrelevement = sample,
        codeCommune = "45234",
        datePrelevement = date,
        codeParametre = parameter,
        libelleParametre = label,
        resultatAlphanumerique = text,
        resultatNumerique = number,
        codeLieuAnalyse = place,
    )
}

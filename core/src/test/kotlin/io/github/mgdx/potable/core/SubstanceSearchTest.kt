package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.ResultRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SubstanceSearchTest {
    private fun row(
        sample: String,
        date: String,
        code: String?,
        label: String,
        text: String? = "0,02",
        value: Double? = 0.02,
        unit: String? = "µg/L",
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
        codeLieuAnalyse = place,
    )

    @Test
    fun `one match per code, with its latest measure and its number of samples`() {
        val matches = SubstanceSearch.group(
            listOf(
                row("P3", "2026-08-26T12:50:00Z", "1830", "Atrazine déséthyl", "0,03", 0.03),
                row("P2", "2025-05-02T10:00:00Z", "1830", "Atrazine déséthyl", "0,05", 0.05),
                row("P2", "2025-05-02T10:00:00Z", "1107", "Atrazine", "0,01", 0.01),
                row("P1", "2024-01-10T10:00:00Z", "1830", "Atrazine déséthyl", "0,04", 0.04),
            ),
        )

        assertEquals(listOf("1107", "1830"), matches.map { it.parameterCode })
        val desethyl = matches[1]
        assertEquals("Atrazine déséthyl", desethyl.label)
        assertEquals("µg/L", desethyl.unit)
        assertEquals(3, desethyl.measureCount)
        assertEquals("P3", desethyl.latest.sampleCode)
        assertEquals("0,03", desethyl.latest.text)
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), desethyl.latest.date)
        assertEquals(setOf("1830"), desethyl.parameterCodes)
    }

    @Test
    fun `a censored latest measure is kept as published`() {
        val match = SubstanceSearch.group(
            listOf(
                row("P2", "2026-08-26T12:50:00Z", "1107", "Atrazine", "<0,02", 0.02),
                row("P1", "2025-05-02T10:00:00Z", "1107", "Atrazine", "0,04", 0.04),
            ),
        ).single()

        assertEquals("<0,02", match.latest.text)
        assertTrue(match.latest.censored)
    }

    @Test
    fun `a sum below its threshold stays SEUIL`() {
        val match = SubstanceSearch.group(
            listOf(
                row("P1", "2026-08-26T12:50:00Z", "6276", "Total des pesticides", "<SEUIL", 0.0),
            ),
        ).single()

        assertEquals("<SEUIL", match.latest.text)
    }

    @Test
    fun `a sample counts once, its laboratory measure first`() {
        val match = SubstanceSearch.group(
            listOf(
                row("P1", "2026-08-26T12:50:00Z", "1302", "pH", "7,3", 7.3, "unité pH", "T"),
                row("P1", "2026-08-26T12:50:00Z", "1302", "pH", "7,58", 7.58, "unité pH", "L"),
            ),
        ).single()

        assertEquals(1, match.measureCount)
        assertEquals("7,58", match.latest.text)
    }

    @Test
    fun `the codes of a featured parameter make one match`() {
        val match = SubstanceSearch.group(
            listOf(
                row("P2", "2026-08-26T12:50:00Z", "7972", "Titre hydrotimétrique", "25", 25.0),
                row("P1", "2025-05-02T10:00:00Z", "1345", "Titre hydrotimétrique", "24", 24.0),
            ),
        ).single()

        assertEquals(setOf("7972", "1345"), match.parameterCodes)
        assertEquals("7972", match.parameterCode)
        assertEquals(2, match.measureCount)
    }

    @Test
    fun `matches are sorted by label in dictionary order, accents and case aside`() {
        val matches = SubstanceSearch.group(
            listOf(
                row("P1", "2026-08-26T12:50:00Z", "3", "propazine"),
                row("P1", "2026-08-26T12:50:00Z", "2", "Éthylène"),
                row("P1", "2026-08-26T12:50:00Z", "1", "Atrazine"),
                row("P1", "2026-08-26T12:50:00Z", "4", "Zinc"),
            ),
        )

        assertEquals(listOf("Atrazine", "Éthylène", "propazine", "Zinc"), matches.map { it.label })
    }

    @Test
    fun `rows without a parameter code are ignored`() {
        val matches = SubstanceSearch.group(
            listOf(row("P1", "2026-08-26T12:50:00Z", null, "Prélèvement sous accréditation")),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `a query is trimmed and must have three characters`() {
        assertEquals("atrazine déséthyl", SubstanceSearch.normalize("  atrazine   déséthyl "))
        assertFalse(SubstanceSearch.isSearchable(" pH  "))
        assertTrue(SubstanceSearch.isSearchable(" eau "))
    }
}

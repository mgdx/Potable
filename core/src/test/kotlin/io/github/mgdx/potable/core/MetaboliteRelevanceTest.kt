package io.github.mgdx.potable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaboliteRelevanceTest {
    @Test
    fun `known codes have the ANSES classification`() {
        assertEquals(Relevance.RELEVANT, MetaboliteRelevance.of("6808")) // 1,2,4-triazole
        assertEquals(Relevance.RELEVANT, MetaboliteRelevance.of("2011")) // 2,6-dichlorobenzamide
        assertEquals(Relevance.RELEVANT, MetaboliteRelevance.of("6855")) // alachlore OXA
        assertEquals(Relevance.NOT_RELEVANT, MetaboliteRelevance.of("1907")) // AMPA
        assertEquals(Relevance.NOT_RELEVANT, MetaboliteRelevance.of("6800")) // alachlore ESA
        // chlorothalonil R471811, matched by its identifier (no CAS)
        assertEquals(Relevance.NOT_RELEVANT, MetaboliteRelevance.of("8865"))
        assertEquals(Relevance.RELEVANT, MetaboliteRelevance.of("7717")) // chlorothalonil R417888
    }

    @Test
    fun `parent substances and unknown codes have none`() {
        assertNull(MetaboliteRelevance.of("1473")) // chlorothalonil, the active substance
        assertNull(MetaboliteRelevance.of("1340")) // nitrates
        assertNull(MetaboliteRelevance.of(""))
        assertNull(MetaboliteRelevance.of("0000"))
    }

    @Test
    fun `the embedded table holds every row of the ANSES table`() {
        val entries = MetaboliteRelevance.entries
        assertEquals(23, entries.size)
        assertEquals(9, entries.count { it.relevance == Relevance.RELEVANT })
        assertEquals(14, entries.count { it.relevance == Relevance.NOT_RELEVANT })
        val cas = Regex("""\d{2,7}-\d{2}-\d""")
        entries.forEach { entry ->
            assertTrue(entry.name, entry.cas == "/" || cas.matches(entry.cas))
            assertTrue(entry.name, entry.opinionYear in 2018..2025)
            assertTrue(entry.name, entry.opinion.startsWith("Avis 20"))
        }
    }

    @Test
    fun `each matched code is numeric, unique and matched by a stated method`() {
        val matched = MetaboliteRelevance.entries.filter { it.parameterCode != null }
        assertEquals(MATCHED, matched.size)
        val codes = matched.map { it.parameterCode!! }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertTrue(it, it.all(Char::isDigit)) }
        matched.forEach { assertTrue(it.name, it.matchedBy in setOf("cas", "identifiant")) }
        // A match on the CAS number needs one in the ANSES table.
        matched.filter { it.matchedBy == "cas" }.forEach { assertTrue(it.name, it.cas != "/") }
    }

    @Test
    fun `a malformed table is rejected`() {
        val header = "nom;cas;statut;annee_avis;reference;code_parametre;appariement"
        assertThrows(IllegalArgumentException::class.java) {
            MetaboliteRelevance.parse(sequenceOf(header, "x;1-11-1;Peut-être;2020;Avis 2020;1;cas"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetaboliteRelevance.parse(sequenceOf(header, "x;1-11-1;Pertinent;2020;Avis 2020;1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetaboliteRelevance.parse(sequenceOf(header, "x;1-11-1;Pertinent;2020;Avis 2020;1;"))
        }
    }

    private companion object {
        const val MATCHED = 23
    }
}

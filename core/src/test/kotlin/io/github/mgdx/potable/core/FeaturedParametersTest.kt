package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.model.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedParametersTest {
    private val latest = Samples.group(
        HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234.json")).data,
    ).first()

    private fun result(code: String, text: String) =
        AnalysisResult(code, "Titre hydrotimétrique", "N", text, null, "°f", null, null)

    @Test
    fun `codes checked against the API map to their parameter`() {
        assertEquals(FeaturedParameter.NITRATES, FeaturedParameter.of("1340"))
        assertEquals(FeaturedParameter.HARDNESS, FeaturedParameter.of("7972"))
        assertEquals(FeaturedParameter.HARDNESS, FeaturedParameter.of("1345"))
        assertEquals(FeaturedParameter.PH, FeaturedParameter.of("1302"))
        assertEquals(FeaturedParameter.FREE_CHLORINE, FeaturedParameter.of("1398"))
    }

    @Test
    fun `neighbouring parameters are not featured`() {
        assertNull(FeaturedParameter.of("1399")) // Chlore total
        assertNull(FeaturedParameter.of("1347")) // Titre alcalimétrique complet
        assertNull(FeaturedParameter.of("6488")) // pH d'équilibre
        assertNull(FeaturedParameter.of("6374")) // Nitrates/50 + Nitrites/3
    }

    @Test
    fun `a code belongs to one parameter only`() {
        val codes = FeaturedParameter.entries.flatMap { it.codes }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the four parameters of the Orleans sample are found, in table order`() {
        val present = FeaturedParameters.present(latest.results)
        assertEquals(FeaturedParameter.entries, present.map { it.first })
        assertEquals(
            listOf("4,40", "12,10", "7,6", "0,12"),
            present.map { it.second.resultText },
        )
    }

    @Test
    fun `the first result of a parameter published under two codes is kept`() {
        val present = FeaturedParameters.present(listOf(result("1345", "20"), result("7972", "21")))
        assertEquals(listOf(FeaturedParameter.HARDNESS), present.map { it.first })
        assertEquals("20", present.single().second.resultText)
    }

    @Test
    fun `no featured parameter gives an empty list`() {
        assertTrue(FeaturedParameters.present(listOf(result("1347", "12"))).isEmpty())
    }
}

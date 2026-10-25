package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class CommuneIndexTest {
    companion object {
        private const val STRASBOURG = "67482"
        private lateinit var index: CommuneIndex

        @BeforeClass
        @JvmStatic
        fun buildIndex() {
            val communes = Fixtures.communes
            val start = System.nanoTime()
            index = CommuneIndex(communes)
            println("CommuneIndex built in %.0f ms".format((System.nanoTime() - start) / 1e6))
        }
    }

    private fun first(query: String) = index.search(query).first()

    @Test
    fun `the exact name comes first`() {
        assertEquals("45234", first("orleans").code)
        assertEquals("45234", first("ORLÉANS").code)
        assertEquals("45234", first("  Orléans ").code)
    }

    @Test
    fun `postal code`() {
        assertEquals("45234", first("45000").code)
        assertEquals("45234", first("45100").code)
        val prefix = index.search("4500", limit = 50)
        assertTrue(prefix.any { it.code == "45234" })
        assertTrue(prefix.all { c -> c.postalCodes.any { it.startsWith("4500") } })
    }

    @Test
    fun `exact postal code, then prefix, then INSEE code`() {
        // 45234 is the INSEE code of Orléans but nobody's postal code.
        assertEquals(listOf("45234"), index.search("45234").map { it.code })
        val paris = index.search("75015")
        assertEquals("75056", paris[0].code)
        assertEquals("75115", paris[1].code)
    }

    @Test
    fun `saint abbreviation`() {
        assertEquals("45284", first("st jean de braye").code)
        assertEquals("45284", first("Saint-Jean-de-Braye").code)
        assertEquals("45284", first("saint jean de br").code)
    }

    @Test
    fun `Paris arrondissement`() {
        assertEquals("75115", first("paris 15").code)
        assertEquals("75115", first("paris 15e").code)
        assertEquals("75115", first("Paris 15e arrondissement").code)
    }

    @Test
    fun `a current commune comes before an arrondissement of the same rank`() {
        val results = index.search("paris")
        assertEquals("75056", results[0].code)
        val sameRank = results.drop(1).takeWhile {
            TextNormalizer.normalize(it.name).startsWith("paris")
        }
        val firstArrondissement = sameRank.indexOfFirst { it.hubEauCode != null }
        assertTrue(firstArrondissement >= 0)
        assertTrue(sameRank.drop(firstArrondissement).all { it.hubEauCode != null })
    }

    @Test
    fun apostrophe() {
        val results = index.search("l'isle")
        assertTrue(results.isNotEmpty())
        val name = results.first().name
        assertTrue(name, TextNormalizer.normalize(name).startsWith("l isle"))
        assertTrue(results.any { it.name == "L'Isle-d'Espagnac" })
        assertEquals(results, index.search("L’Isle"))
        assertEquals(results, index.search("l isle"))
    }

    @Test
    fun `exact, then prefix, then word, then contains`() {
        val small = CommuneIndex(
            listOf(
                commune("1", "Contient Bray Ici"),
                commune("2", "La Bray Haute"),
                commune("3", "Braye"),
                commune("4", "Bray"),
                commune("5", "Ambrayville"),
            ),
        )
        assertEquals(listOf("4", "3", "2", "1", "5"), small.search("bray").map { it.code })
    }

    @Test
    fun `ties go to the current commune, then the shortest name, then alphabetical`() {
        val small = CommuneIndex(
            listOf(
                commune("1", "Saint-Martin-Longue"),
                commune("2", "Saint-Martin-B", hubEauCode = "9"),
                commune("3", "Saint-Martin-C"),
                commune("4", "Saint-Martin-A"),
            ),
        )
        assertEquals(listOf("4", "3", "1", "2"), small.search("saint martin").map { it.code })
    }

    @Test
    fun `the limit is respected`() {
        assertEquals(20, index.search("saint").size)
        assertEquals(5, index.search("saint", limit = 5).size)
        assertEquals(1, index.search("saint", limit = 1).size)
        assertTrue(index.search("saint", limit = 0).isEmpty())
        assertEquals(30, index.search("45", limit = 30).size)
    }

    @Test
    fun `an empty or unknown query gives nothing`() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
        assertTrue(index.search("-'").isEmpty())
        assertTrue(index.search("zzzzqqq").isEmpty())
        assertTrue(index.search("99999").isEmpty())
    }

    @Test
    fun `nearest current commune or arrondissement`() {
        // Next to the Orléans centroid.
        assertEquals("45234", index.nearest(47.875, 1.91)?.code)
        // Paris 15e centroid: the arrondissement, not Vanves next door.
        val paris15 = index.nearest(48.8417, 2.2937)
        assertEquals("75115", paris15?.code)
        assertEquals("75056", paris15?.queryCode)
        // Lyon 1er centroid.
        assertEquals("69381", index.nearest(45.7701, 4.8264)?.code)
        val delegated = CommuneIndex(
            listOf(
                Commune("1", "Actuelle", emptyList(), 1.0, 1.0),
                Commune("2", "Déléguée", emptyList(), 0.0, 0.0, hubEauCode = "1"),
            ),
        )
        assertEquals("1", delegated.nearest(0.0, 0.0)?.code)
        assertNull(CommuneIndex(emptyList()).nearest(0.0, 0.0))
    }

    @Test
    fun `nearest gives up beyond the maximum distance`() {
        val one = CommuneIndex(listOf(Commune("1", "Seule", emptyList(), 45.0, 0.0)))
        // One degree of latitude is about 111.2 km.
        assertEquals("1", one.nearest(46.0, 0.0)?.code)
        assertEquals("1", one.nearest(46.0, 0.0, maxDistanceKm = 112.0)?.code)
        assertNull(one.nearest(46.0, 0.0, maxDistanceKm = 110.0))
        assertEquals("1", one.nearest(45.1, 0.0, maxDistanceKm = 20.0)?.code)
        assertNull(one.nearest(45.2, 0.0, maxDistanceKm = 20.0))
    }

    @Test
    fun `at equal rank the largest population comes first, unknown last`() {
        val small = CommuneIndex(
            listOf(
                commune("1", "Saint-A", population = 100),
                commune("2", "Saint-Bbbbbbb", population = 5_000),
                commune("3", "Saint-C"),
                commune("4", "Saint-D", population = 100),
                commune("5", "Saint-E", hubEauCode = "2", population = 1_000_000),
                commune("6", "Saint-F", hubEauCode = "2"),
            ),
        )
        assertEquals(listOf("2", "1", "4", "3", "5", "6"), small.search("saint").map { it.code })
        // The textual rank still prevails: an exact name beats a larger prefix match.
        assertEquals("1", small.search("saint a").first().code)
    }

    @Test
    fun `large cities come first on short queries`() {
        assertTrue(index.search("st").any { it.code == STRASBOURG })
        assertEquals(STRASBOURG, first("st").code)
        val saint = index.search("saint").take(3).map { it.code }
        // Saint-Étienne, Saint-Denis (La Réunion), Saint-Paul (La Réunion).
        assertTrue(saint.toString(), saint.any { it in setOf("42218", "97411", "97415") })
        // Three communes are named exactly "Mont" (and three "Mars"): the
        // exact rank still comes first, then the largest prefix match.
        val mont = index.search("mont")
        assertEquals(listOf("Mont", "Mont", "Mont"), mont.take(3).map { it.name })
        assertTrue(mont[3].name in setOf("Montpellier", "Montreuil"))
        val mars = index.search("mars").map { it.name }
        assertEquals("Marseille", mars.first { it != "Mars" })
    }

    @Test
    fun `by code`() {
        assertEquals("Orléans", index.byCode("45234")?.name)
        assertEquals("Paris", index.byCode("75056")?.name)
        assertEquals("75056", index.byCode("75115")?.hubEauCode)
        assertEquals("La Chapelle-Rousselin", index.byCode("49074")?.name)
        assertNull(index.byCode("99999"))
        assertNull(index.byCode(""))
    }

    @Test
    fun `a last word st may be a prefix being typed`() {
        // 36 names start with "St"; ranked by population, Strasbourg first.
        val st = index.search("st", limit = 60)
        assertTrue(st.any { it.code == STRASBOURG })
        assertEquals(st, index.search("St", limit = 60))
        // At equal rank, the literal "st…" names come before "saint…".
        assertTrue(index.search("st").all { it.name.startsWith("St") })
        assertTrue(st.any { it.name.startsWith("Saint-") })
        assertTrue(index.search("ste", limit = 60).any { it.name.startsWith("Sainte-") })
        assertTrue(index.search("str").any { it.code == STRASBOURG })
        assertEquals(STRASBOURG, first("stras").code)
        assertEquals("45284", first("st jean de braye").code)
        assertEquals("45284", first("st jean de bra").code)
    }

    @Test
    fun `search speed`() {
        val queries = listOf(
            "orleans",
            "saint",
            "a",
            "l isle",
            "st jean de braye",
            "paris 15",
            "45000",
            "7",
            "zzz",
        )
        repeat(5) { queries.forEach { index.search(it) } } // warm-up
        val rounds = 20
        val start = System.nanoTime()
        repeat(rounds) { queries.forEach { index.search(it) } }
        val averageMs = (System.nanoTime() - start) / 1e6 / (rounds * queries.size)
        println("CommuneIndex.search: %.2f ms per query".format(averageMs))
        // Not a benchmark, only a guard against an accidentally quadratic
        // search: the target is < 50 ms, see the printed average.
        assertTrue("average $averageMs ms", averageMs < 500)
    }

    private fun commune(
        code: String,
        name: String,
        hubEauCode: String? = null,
        population: Int? = null,
    ) = Commune(code, name, emptyList(), 0.0, 0.0, hubEauCode, population)
}

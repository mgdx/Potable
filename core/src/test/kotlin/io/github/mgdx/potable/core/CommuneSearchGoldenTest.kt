package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.security.MessageDigest

/**
 * Non-regression of the search over the real embedded referential: the
 * results of [QUERIES] (order included) and digests of many more searches and
 * "near me" lookups, as recorded in resources/communes/search-golden.txt.
 * A deliberate change of the ranking updates that file (see its header).
 */
class CommuneSearchGoldenTest {
    companion object {
        private const val GOLDEN = "/communes/search-golden.txt"
        private lateinit var index: CommuneIndex
        private lateinit var expected: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun setUp() {
            index = CommuneIndex(Fixtures.communes)
            val url = checkNotNull(CommuneSearchGoldenTest::class.java.getResource(GOLDEN))
            expected = url.readText().lines()
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { it.substringBefore('\t') to it.substringAfter('\t') }
        }

        /** Accents, apostrophes, abbreviations, postal and INSEE codes, homonyms, overseas, Corsica. */
        val QUERIES = listOf(
            "orleans", "ORLÉANS", "  Orléans ", "parisot", "Parisot", "saint verand",
            "Saint-Vérand", "st verand", "81310", "82160", "38160", "69620", "45234",
            "45", "4500", "75015", "75", "7", "0", "97", "97411", "2A004", "2a004",
            "2B033", "ajaccio", "bastia", "saint denis", "Saint-Denis", "st", "ste",
            "str", "stras", "st jean de braye", "ste marie", "sainte marie", "l'isle",
            "L’Isle", "cœuvres", "coeuvres", "oe", "paris", "paris 15", "paris 15e",
            "lyon", "marseille 1", "mont", "mars", "la chapelle", "beaumont", "é",
            "a", "y", "fort de france", "mamoudzou", "cayenne", "saint pierre",
            "zzz", "99999", "-'",
        )

        /** Places for [CommuneIndex.nearest]: a grid over mainland France and Corsica, plus overseas. */
        private val POINTS: List<Pair<Double, Double>> = buildList {
            for (i in 0..21) {
                for (j in 0..31) add(41.0 + i * 0.5 to -5.5 + j * 0.5)
            }
            add(16.25 to -61.58) // Guadeloupe
            add(14.6 to -61.07) // Martinique
            add(4.93 to -52.33) // Guyane
            add(-21.11 to 55.53) // La Réunion
            add(-12.78 to 45.23) // Mayotte
            add(46.78 to -56.17) // Saint-Pierre
            add(0.0 to 0.0) // nowhere
        }

        /** Many more queries: name, name prefixes, code and postal prefix of one entry out of 37. */
        fun bulkQueries(communes: List<Commune>): List<String> = buildList {
            communes.forEachIndexed { i, c ->
                if (i % 37 != 0) return@forEachIndexed
                add(c.name)
                val normalized = TextNormalizer.normalize(c.name)
                add(normalized.take(3))
                add(normalized.take(6))
                add(c.code)
                c.postalCodes.firstOrNull()?.let { add(it.take(3)) }
            }
        }

        fun codes(found: List<Commune>) = found.joinToString(",") { it.code }

        private fun digest(lines: Sequence<String>): String {
            val sha = MessageDigest.getInstance("SHA-256")
            lines.forEach { sha.update((it + "\n").toByteArray()) }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }

        /** Every field of every result, not only the codes. */
        fun searchDigest(index: CommuneIndex, queries: List<String>) =
            digest(queries.asSequence().map { q -> q + "\t" + index.search(q).joinToString("|") })

        fun nearestDigest(index: CommuneIndex) = digest(
            POINTS.asSequence().flatMap { (lat, lon) ->
                sequenceOf(
                    index.nearest(lat, lon).toString(),
                    index.nearest(lat, lon, maxDistanceKm = 20.0).toString(),
                )
            },
        )
    }

    @Test
    fun `hand-picked queries give the recorded results, in order`() {
        QUERIES.forEach { q ->
            assertEquals("query \"$q\"", expected.getValue(q), codes(index.search(q)))
        }
        assertEquals(expected.getValue("@full"), searchDigest(index, QUERIES))
    }

    @Test
    fun `many more queries give the recorded results`() {
        val queries = bulkQueries(Fixtures.communes)
        assertEquals(expected.getValue("@bulk"), searchDigest(index, queries))
    }

    @Test
    fun `nearest gives the recorded communes`() {
        assertEquals(expected.getValue("@nearest"), nearestDigest(index))
    }

    @Test
    fun `every entry is found by its code`() {
        Fixtures.communes.forEach { assertEquals(it, index.byCode(it.code)) }
    }
}

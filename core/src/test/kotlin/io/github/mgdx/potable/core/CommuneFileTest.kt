package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CommuneFileTest {
    private val header = "# potable-communes v1 source=https://geo.api.gouv.fr generated=2026-10-25"

    @Test
    fun `parses the contract format`() {
        val communes = CommuneFile.parse(
            sequenceOf(
                header,
                "45234\tOrléans\t45000,45100\t47.8734\t1.9122\t",
                "75115\tParis 15e Arrondissement\t75015\t48.8417\t2.2937\t75056",
                "49071\tChanzeaux\t\t47.2633\t-0.6436\t49092",
                "",
            ),
        )
        val expected = listOf(
            Commune("45234", "Orléans", listOf("45000", "45100"), 47.8734, 1.9122),
            Commune("75115", "Paris 15e Arrondissement", listOf("75015"), 48.8417, 2.2937, "75056"),
            Commune("49071", "Chanzeaux", emptyList(), 47.2633, -0.6436, "49092"),
        )
        assertEquals(expected, communes)
        assertEquals("45234", communes[0].queryCode)
        assertEquals("75056", communes[1].queryCode)
    }

    @Test
    fun `parses format v2 with a population`() {
        val communes = CommuneFile.parse(
            sequenceOf(
                "# potable-communes v2 source=https://geo.api.gouv.fr " +
                    "events=v_mvt_commune_2026 generated=2026-10-25",
                "45234\tOrléans\t45000,45100\t47.8734\t1.9122\t\t116357",
                "49071\tChanzeaux\t\t47.2633\t-0.6436\t49092\t",
                "75115\tParis 15e\t75015\t48.8417\t2.2937\t75056\t0",
            ),
        )
        val expected = listOf(
            Commune("45234", "Orléans", listOf("45000", "45100"), 47.8734, 1.9122, null, 116357),
            Commune("49071", "Chanzeaux", emptyList(), 47.2633, -0.6436, "49092"),
            Commune("75115", "Paris 15e", listOf("75015"), 48.8417, 2.2937, "75056", 0),
        )
        assertEquals(expected, communes)
    }

    @Test
    fun `a malformed line reports its number`() {
        val cases = mapOf(
            "45234\tOrléans\t45000\t47.8\t1.9" to "expected 6 or 7 columns",
            "45234\tOrléans\t45000\t47.8\t1.9\t\t1\textra" to "expected 6 or 7 columns",
            "45234\tOrléans\t45000\t47.8\t1.9\t\tmany" to "invalid population",
            "45234\tOrléans\t45000\t47.8\t1.9\t\t-3" to "invalid population",
            "45234\tOrléans\t45000\t47.8\t1.9\t\t99999999999" to "invalid population",
            "45234\tOrléans\t45000\tnord\t1.9\t" to "invalid latitude",
            "45234\tOrléans\t45000\t47.8\t\t" to "invalid longitude",
            "45234\tOrléans\t45000\t47.8\tNaN\t" to "invalid longitude",
            "\tOrléans\t45000\t47.8\t1.9\t" to "empty code",
            "45234\t \t45000\t47.8\t1.9\t" to "empty name",
        )
        for ((line, reason) in cases) {
            try {
                CommuneFile.parse(sequenceOf(header, "01001\tA\t01400\t46.1\t4.9\t", line))
                fail("expected IllegalArgumentException for: $line")
            } catch (e: IllegalArgumentException) {
                val message = e.message.orEmpty()
                assertTrue(message, message.startsWith("line 3:"))
                assertTrue(message, message.contains(reason))
            }
        }
    }

    @Test
    fun `parses the embedded file`() {
        val communes = Fixtures.communes
        val lines = Fixtures.communesFile.readLines()
        assertEquals(lines.count { it.isNotEmpty() && !it.startsWith("#") }, communes.size)
        assertTrue("about 37,000 entries, got ${communes.size}", communes.size in 35_000..40_000)
        assertEquals(communes.size, communes.map { it.code }.toSet().size)

        val orleans = communes.single { it.code == "45234" }
        assertEquals("Orléans", orleans.name)
        assertTrue(orleans.postalCodes.contains("45000"))
        assertNull(orleans.hubEauCode)
        assertEquals("75056", communes.single { it.code == "75115" }.hubEauCode)
        assertTrue(checkNotNull(orleans.population) > 100_000)
        assertTrue(communes.filter { it.hubEauCode == null }.all { it.population != null })
        // Former code absorbed by a commune nouvelle (INSEE events), no delegated status.
        val former = communes.single { it.code == "49074" }
        assertEquals("La Chapelle-Rousselin", former.name)
        assertEquals("49092", former.hubEauCode)
        assertNull(former.population)

        val current = communes.filter { it.hubEauCode == null }.map { it.code }.toSet()
        communes.mapNotNull { it.hubEauCode }.forEach { assertTrue(it, it in current) }
    }
}

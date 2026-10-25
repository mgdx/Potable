package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.hubeau.UdiRow
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.core.model.UdiChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class UdiSelectionTest {
    private fun fixture(name: String) = HubEauParser.parseCommunesUdi(Fixtures.hubeau(name)).data

    @Test
    fun `Orleans has a single UDI, named as in the latest year`() {
        val name = "ORLEANS-ST JEAN LE BLANC-ST PRYVÉ-ST DENIS EN VAL"
        assertEquals(
            UdiChoice.Single(Udi("045000474", name, null, 2026)),
            UdiSelection.choose(fixture("communes_udi_45234.json")),
        )
    }

    @Test
    fun `Paris has four UDI sorted by name`() {
        val expected = listOf(
            Udi("075000221", "CENTRE", "du 1° au 13°, 15° et 16° arrondissement", 2026),
            Udi("075000227", "EST", "12°,19°, 20°, est des 10, 11,18", 2026),
            Udi("075000222", "NORD OUEST", "8° à 10°, 16° à 18° arrondissement", 2026),
            Udi("075000225", "SUD OUEST", "5°, 6°, 13° à 16° arrondissement", 2026),
        )
        assertEquals(
            UdiChoice.Multiple(expected),
            UdiSelection.choose(fixture("communes_udi_75056.json")),
        )
    }

    @Test
    fun `no data gives none`() {
        assertEquals(UdiChoice.None, UdiSelection.choose(fixture("communes_udi_97501.json")))
        assertEquals(UdiChoice.None, UdiSelection.choose(emptyList()))
    }

    @Test
    fun `a change of UDI from one year to the next keeps the latest year only`() {
        val rows = listOf(
            row("OLD1", "ANCIEN NORD", "2024"),
            row("OLD2", "ANCIEN SUD", "2024"),
            row("NEW", "NOUVEAU", "2025"),
            row("OLD1", "ANCIEN NORD", "2023"),
        )
        assertEquals(UdiChoice.Single(Udi("NEW", "NOUVEAU", null, 2025)), UdiSelection.choose(rows))
    }

    @Test
    fun `districts of one network are joined without duplicates`() {
        val rows = listOf(
            row("R1", "RESEAU", "2026", "Nord"),
            row("R1", "RESEAU", "2026", "Centre"),
            row("R1", "RESEAU", "2026", "Nord"),
            row("R1", "RESEAU", "2026", " "),
        )
        val expected = Udi("R1", "RESEAU", "Nord, Centre", 2026)
        assertEquals(UdiChoice.Single(expected), UdiSelection.choose(rows))
    }

    @Test
    fun `the whole commune or a missing district gives no district`() {
        for (district in listOf("totalité", "Totalite", "", null)) {
            val choice = UdiSelection.choose(listOf(row("R1", "RESEAU", "2026", district)))
            assertEquals(UdiChoice.Single(Udi("R1", "RESEAU", null, 2026)), choice)
        }
    }

    @Test
    fun `a row with an unreadable year is ignored`() {
        val rows = listOf(row("R1", "A", "2026"), row("R2", "B", null), row("R3", "C", "deux"))
        assertEquals(UdiChoice.Single(Udi("R1", "A", null, 2026)), UdiSelection.choose(rows))
        assertEquals(UdiChoice.None, UdiSelection.choose(listOf(row("R2", "B", null))))
    }

    @Test
    fun `a missing name falls back on the code`() {
        val choice = UdiSelection.choose(listOf(row("R1", null, "2026")))
        assertEquals(UdiChoice.Single(Udi("R1", "R1", null, 2026)), choice)
    }

    @Test
    fun `the sort ignores accents and case`() {
        val rows = listOf(
            row("R1", "Zone", "2026"),
            row("R2", "ÉTANG", "2026"),
            row("R3", "abbaye", "2026"),
        )
        val choice = UdiSelection.choose(rows) as UdiChoice.Multiple
        assertEquals(listOf("R3", "R2", "R1"), choice.udis.map { it.code })
    }

    private fun row(code: String, name: String?, year: String?, district: String? = "totalité") =
        UdiRow(
            codeCommune = "00000",
            nomQuartier = district,
            codeReseau = code,
            nomReseau = name,
            annee = year,
        )
}

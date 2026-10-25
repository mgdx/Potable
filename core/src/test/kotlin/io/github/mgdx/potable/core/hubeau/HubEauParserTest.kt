package io.github.mgdx.potable.core.hubeau

import io.github.mgdx.potable.core.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HubEauParserTest {
    private fun results(name: String) = HubEauParser.parseResultats(Fixtures.hubeau(name))

    @Test
    fun `parses the UDI of Orleans`() {
        val page = HubEauParser.parseCommunesUdi(Fixtures.hubeau("communes_udi_45234.json"))
        assertEquals(11, page.count)
        assertNull(page.next)
        assertEquals(11, page.data.size)
        val first = page.data.first()
        assertEquals("45234", first.codeCommune)
        assertEquals("ORLEANS", first.nomCommune)
        assertEquals("totalité", first.nomQuartier)
        assertEquals("045000474", first.codeReseau)
        assertEquals("ORLEANS-ST JEAN LE BLANC-ST PRYVÉ-ST DENIS EN VAL", first.nomReseau)
        assertEquals("2020-01-01", first.debutAlim)
        assertEquals("2026", first.annee)
    }

    @Test
    fun `parses the UDI of Paris`() {
        val page = HubEauParser.parseCommunesUdi(Fixtures.hubeau("communes_udi_75056.json"))
        assertEquals(44, page.count)
        assertEquals(44, page.data.size)
        assertEquals(4, page.data.map { it.codeReseau }.toSet().size)
    }

    @Test
    fun `parses an empty UDI page`() {
        val page = HubEauParser.parseCommunesUdi(Fixtures.hubeau("communes_udi_97501.json"))
        assertEquals(0, page.count)
        assertTrue(page.data.isEmpty())
        assertNull(page.next)
    }

    @Test
    fun `parses a results page`() {
        val page = results("resultats_dis_45234.json")
        assertEquals(79087, page.count)
        assertEquals(
            "https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/resultats_dis" +
                "?code_commune=45234&sort=desc&page=2&size=100",
            page.next,
        )
        assertEquals(100, page.data.size)
        val row = page.data.first()
        assertEquals("04500171467", row.codePrelevement)
        assertEquals("45234", row.codeCommune)
        assertNull(row.codeReseau)
        assertEquals("045000474", row.reseaux?.first()?.code)
        assertEquals("100 %", row.reseaux?.get(1)?.debit)
        assertNull(row.reseaux?.first()?.debit)
        assertEquals("2026-08-26T12:50:00Z", row.datePrelevement)
        assertEquals("C", row.conformiteLimitesBactPrelevement)
        assertEquals("1339", row.codeParametre)
        assertEquals("Nitrites (en NO2)", row.libelleParametre)
        assertEquals("<0,01", row.resultatAlphanumerique)
        assertEquals(0.0, row.resultatNumerique!!, 0.0)
        assertEquals("mg/L", row.libelleUnite)
        assertEquals("<=0,5 mg/L", row.limiteQualiteParametre)
        assertNull(row.referenceQualiteParametre)
        assertEquals("SUEZ AQUALIGE", row.nomDistributeur)
        assertEquals("ORLEANS METROPOLE", row.nomMoa)
    }

    @Test
    fun `a non-numeric result has no number`() {
        val rows = results("resultats_dis_45234.json").data
        assertNull(rows.first { it.resultatAlphanumerique == "N.M." }.resultatNumerique)
    }

    @Test
    fun `absent, null and empty compliance fields are accepted`() {
        val rows = results("resultats_dis_sans_conclusion.json").data
        assertEquals(3, rows.size)
        rows.forEach {
            assertNull(it.conclusionConformitePrelevement)
            assertEquals("", it.conformiteLimitesBactPrelevement)
            assertNull(it.conformiteLimitesPcPrelevement)
            assertNull(it.conformiteReferencesBactPrelevement)
        }
    }

    @Test
    fun `malformed UDI pages throw a format exception`() {
        val udi = """{"code_commune":"45234","code_reseau":"045000474"}"""
        val cases = listOf(
            "",
            "not json",
            "{",
            "[]",
            "null",
            """{"data":[]}""",
            """{"count":0}""",
            """{"count":"many","data":[]}""",
            """{"count":1,"data":{}}""",
            """{"count":1,"data":[{"code_commune":"45234"}]}""",
            """{"count":1,"data":[{"code_commune":"45234","code_reseau":null}]}""",
            """{"count":1,"data":[{"code_commune":"4","code_reseau":"x","annee":[1]}]}""",
            """{"count":1,"data":[$udi,]}""",
        )
        for (json in cases) {
            assertFormatException(json) { HubEauParser.parseCommunesUdi(it) }
        }
    }

    @Test
    fun `malformed result rows throw a format exception`() {
        val ids = """"code_prelevement":"1","code_commune":"2""""
        val cases = listOf(
            """{"count":1,"data":[{"code_commune":"2","code_parametre":"3"}]}""",
            """{"count":1,"data":[{$ids,"code_parametre":"3","resultat_numerique":"abc"}]}""",
            """{"count":1,"data":[{$ids,"code_parametre":"3","reseaux":[{"nom":"x"}]}]}""",
        )
        for (json in cases) {
            assertFormatException(json) { HubEauParser.parseResultats(it) }
        }
    }

    @Test
    fun `a row without code_parametre does not fail the page`() {
        // Toulouse, 2026-08-31: "Prélèvement sous acréditation" has a null code.
        val page = results("resultats_dis_31555_sans_code_parametre.json")
        assertEquals(5, page.data.size)
        val row = page.data.single { it.codeParametre == null }
        assertEquals("03100216050", row.codePrelevement)
        assertEquals("Prélèvement sous acréditation", row.libelleParametre)
        assertEquals("OUI", row.resultatAlphanumerique)
        assertEquals("2026-08-31T14:43:00Z", row.datePrelevement)
        val absent = """{"count":1,"data":[{"code_prelevement":"1","code_commune":"2"}]}"""
        assertNull(HubEauParser.parseResultats(absent).data.single().codeParametre)
    }

    @Test
    fun `unknown fields and null optional fields are accepted`() {
        val json = """
            {"count":1,"extra":true,"data":[{"code_prelevement":"1","code_commune":"2",
            "code_parametre":"3","nouveau":{"a":1},"libelle_parametre":null,"reseaux":null}]}
        """.trimIndent()
        val row = HubEauParser.parseResultats(json).data.single()
        assertNull(row.libelleParametre)
        assertNull(row.reseaux)
    }

    private fun assertFormatException(json: String, parse: (String) -> Unit) {
        try {
            parse(json)
            fail("expected HubEauFormatException for: $json")
        } catch (_: HubEauFormatException) {
            // expected
        }
    }
}

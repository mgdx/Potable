package io.github.mgdx.potable.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    private fun n(text: String) = TextNormalizer.normalize(text)

    @Test
    fun `lowercase without accents`() {
        assertEquals("orleans", n("ORLÉANS"))
        assertEquals("orleans", n("Orléans"))
        assertEquals("ecouen", n("Écouen"))
        assertEquals("pont a mousson", n("Pont-à-Mousson"))
        assertEquals("aiguines", n("Aiguînes"))
        assertEquals("saone", n("Saône"))
        assertEquals("sainte genevieve", n("Sainte-Geneviève"))
    }

    @Test
    fun ligatures() {
        assertEquals("oeuilly", n("Œuilly"))
        assertEquals("coeuvres", n("Cœuvres"))
        assertEquals("laetitia", n("LÆTITIA"))
    }

    @Test
    fun `separators become spaces`() {
        assertEquals("l isle d espagnac", n("L'Isle-d'Espagnac"))
        assertEquals("l isle", n("l’isle"))
        assertEquals("saint jean de braye", n("Saint-Jean-de-Braye"))
        assertEquals("saint jean", n("St. Jean"))
    }

    @Test
    fun `spaces are collapsed and trimmed`() {
        assertEquals("paris 15e", n("  Paris \t 15e  "))
        assertEquals("", n(""))
        assertEquals("", n(" - ' . "))
    }

    @Test
    fun `saint abbreviations as whole words`() {
        assertEquals("saint jean de braye", n("st jean de braye"))
        assertEquals("saint jean de braye", n("St-Jean-de-Braye"))
        assertEquals("sainte foy", n("Ste-Foy"))
        assertEquals("saint", n("ST"))
        assertEquals("strasbourg", n("Strasbourg"))
        assertEquals("estaing", n("Estaing"))
        assertEquals("steenvoorde", n("Steenvoorde"))
    }

    @Test
    fun `the last abbreviation can be kept as typed`() {
        fun keep(text: String) = TextNormalizer.normalizeKeepingLastAbbreviation(text)
        assertEquals("st", keep("St"))
        assertEquals("saint jean ste", keep("st-jean ste"))
        assertEquals(null, keep("st jean"))
        assertEquals(null, keep("str"))
        assertEquals(null, keep(""))
    }

    @Test
    fun idempotent() {
        for (text in listOf("L'Isle-d'Espagnac", "St-Jean", "Œuilly", "Paris 15e Arrondissement")) {
            assertEquals(n(text), n(n(text)))
        }
    }
}

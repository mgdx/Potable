package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.MetaboliteRelevance
import io.github.mgdx.potable.core.Relevance
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaboliteRelevanceNoteTest {
    @Test
    fun `each classification has the label of the sample detail`() {
        assertEquals(R.string.metabolite_relevant, Relevance.RELEVANT.labelRes())
        assertEquals(R.string.metabolite_not_relevant, Relevance.NOT_RELEVANT.labelRes())
    }

    @Test
    fun `the history reads the classification from the code of its series`() {
        // 1,2,4-triazole and acetochlor ESA in the ANSES table.
        assertEquals(Relevance.RELEVANT.labelRes(), MetaboliteRelevance.of("6808")?.labelRes())
        assertEquals(Relevance.NOT_RELEVANT.labelRes(), MetaboliteRelevance.of("6856")?.labelRes())
    }
}

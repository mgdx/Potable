package io.github.mgdx.potable.ui.common

import io.github.mgdx.potable.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParameterTextsTest {
    private val orleans = "45234"
    private val pointeAPitre = "97120"

    @Test
    fun `a featured parameter has its sheet explanation, under each of its codes`() {
        val nitrates = R.string.featured_nitrates_explanation
        val hardness = R.string.featured_hardness_explanation
        assertEquals(nitrates, parameterExplanationRes("1340", orleans))
        assertEquals(hardness, parameterExplanationRes("7972", orleans))
        assertEquals(hardness, parameterExplanationRes("1345", orleans))
    }

    @Test
    fun `a watched substance has its sheet explanation`() {
        assertEquals(R.string.watched_pfas_explanation, parameterExplanationRes("8847", orleans))
        assertEquals(R.string.watched_lead_explanation, parameterExplanationRes("1382", orleans))
    }

    @Test
    fun `chlordecone is explained only where the sheet reads it`() {
        assertEquals(
            R.string.watched_chlordecone_explanation,
            parameterExplanationRes("1866", pointeAPitre),
        )
        assertNull(parameterExplanationRes("1866", orleans))
    }

    @Test
    fun `a parameter without an editorial text has no explanation`() {
        assertNull(parameterExplanationRes("1399", orleans))
    }
}

package io.github.mgdx.potable.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    private val compliant = event(1, unfavourable = false)
    private val unfavourable = event(2, unfavourable = true, conclusion = "Eau non conforme.")
    private val events = listOf(compliant, unfavourable)

    @Test
    fun `new analyses notify every event, whatever the other switch`() {
        assertEquals(
            events,
            notifiable(events, AlertSwitches(newAnalysis = true, unfavourable = false)),
        )
        assertEquals(
            events,
            notifiable(events, AlertSwitches(newAnalysis = true, unfavourable = true)),
        )
    }

    @Test
    fun `unfavourable only notifies the unfavourable events, not a compliant sample`() {
        val selected = notifiable(events, AlertSwitches(newAnalysis = false, unfavourable = true))
        assertEquals(listOf(unfavourable), selected)
        assertEquals(
            emptyList<Any>(),
            notifiable(listOf(compliant), AlertSwitches(newAnalysis = false, unfavourable = true)),
        )
    }

    @Test
    fun `nothing is notified when both alerts are off`() {
        assertEquals(emptyList<Any>(), notifiable(events, AlertSwitches(false, false)))
    }

    @Test
    fun `an unfavourable event goes to its own channel`() {
        assertEquals(AlertChannel.NEW_ANALYSIS, AlertChannel.of(compliant))
        assertEquals(AlertChannel.UNFAVOURABLE, AlertChannel.of(unfavourable))
    }

    @Test
    fun `a short conclusion is shown as published, spaces tidied`() {
        assertEquals(
            "Eau non conforme aux limites de qualité.",
            conclusionText("  Eau non conforme\naux  limites de qualité. "),
        )
    }

    @Test
    fun `no conclusion gives no text`() {
        assertNull(conclusionText(null))
        assertNull(conclusionText("   "))
    }

    @Test
    fun `a long conclusion is cut at a space and ends with an ellipsis`() {
        val long = "Eau d'alimentation non conforme aux limites de qualité, " +
            "consommation déconseillée pour les femmes enceintes et les nourrissons, " +
            "se référer aux recommandations de l'ARS."
        val text = conclusionText(long, max = 60)!!
        assertTrue(text, text.length <= 60)
        assertTrue(text, text.endsWith("…"))
        assertTrue(text, long.startsWith(text.removeSuffix("…")))
        assertEquals("Eau d'alimentation non conforme aux limites de qualité…", text)
    }
}

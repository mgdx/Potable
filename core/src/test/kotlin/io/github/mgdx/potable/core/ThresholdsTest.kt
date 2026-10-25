package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Threshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThresholdsTest {
    private fun assertParsed(text: String, lower: Double?, upper: Double?, unit: String?) =
        assertEquals(text, Threshold(lower, upper, unit), Thresholds.parse(text))

    /** Texts published by Hub'Eau, listed in docs/hubeau-api.md ("Seuils"). */
    @Test
    fun `published upper bounds`() {
        assertParsed("<=50 mg/L", null, 50.0, "mg/L")
        assertParsed("<=0 n/(100mL)", null, 0.0, "n/(100mL)")
        assertParsed("<=0,1 µg/L", null, 0.1, "µg/L")
        assertParsed("<=0.1 µg/L", null, 0.1, "µg/L")
        assertParsed("<=0.01 µg/L", null, 0.01, "µg/L")
        assertParsed("<=0,03 µg/L", null, 0.03, "µg/L")
        assertParsed("<=0,25 mg/L", null, 0.25, "mg/L")
        assertParsed("<=1,5 mg/L", null, 1.5, "mg/L")
        assertParsed("<=2,5 µg/L", null, 2.5, "µg/L")
        assertParsed("<=100 µg/L", null, 100.0, "µg/L")
        assertParsed("<=250 mg/L", null, 250.0, "mg/L")
        assertParsed("<=2 mg(Cu)/L", null, 2.0, "mg(Cu)/L")
        assertParsed("<=50 µg(CN)/L", null, 50.0, "µg(CN)/L")
        assertParsed("<=20 µg(Se)/L", null, 20.0, "µg(Se)/L")
        assertParsed("<=15 mg(Pt)/L", null, 15.0, "mg(Pt)/L")
        assertParsed("<=2 mg(C)/L", null, 2.0, "mg(C)/L")
        assertParsed("<=1 NFU", null, 1.0, "NFU")
        assertParsed("<=0,5 NFU", null, 0.5, "NFU")
        assertParsed("<=25 °C", null, 25.0, "°C")
        assertParsed("<=0,1 mSv/a", null, 0.1, "mSv/a")
        assertParsed("<=100 Bq/L", null, 100.0, "Bq/L")
        assertParsed("<=0.1 mSv/a", null, 0.1, "mSv/a")
        assertParsed("<=0 NPP/100mL", null, 0.0, "NPP/100mL")
        assertParsed("<=2 mg/L", null, 2.0, "mg/L")
    }

    @Test
    fun `published ranges`() {
        assertParsed(">=6,5 et <=9 unité pH", 6.5, 9.0, "unité pH")
        assertParsed(">=200 et <=1100 µS/cm", 200.0, 1100.0, "µS/cm")
        // "SANS OBJET" means "no unit".
        assertParsed(">=1 et <=2 SANS OBJET", 1.0, 2.0, null)
    }

    @Test
    fun `other comparators, spacing and a missing unit`() {
        assertParsed("<1", null, 1.0, null)
        assertParsed(">80 %", 80.0, null, "%")
        assertParsed("≤ 10 µg/L", null, 10.0, "µg/L")
        assertParsed("≥0,5", 0.5, null, null)
        assertParsed("  <=  50   mg/L ", null, 50.0, "mg/L")
        assertParsed("<= 9 et >= 6,5 unité pH", 6.5, 9.0, "unité pH")
        assertParsed(">=6,5 ET<=9", 6.5, 9.0, null)
        assertParsed("=0 n/(100mL)", 0.0, 0.0, "n/(100mL)")
        assertParsed("<=-1 °C", null, -1.0, "°C")
    }

    /**
     * Regression: Orléans pH showed its reference as "not drawn" on the
     * device. The patterns were anchored with \G and read with
     * find(start), which ICU (Android) only matches at the start of the
     * text, so every range parsed as null there; the JVM hid it.
     */
    @Test
    fun `the patterns are not anchored with backslash G`() {
        for (pattern in listOf(Thresholds.BOUND.pattern, Thresholds.JOIN.pattern)) {
            assertFalse(pattern, pattern.contains("\\G"))
        }
    }

    @Test
    fun `nothing interpretable gives null`() {
        assertNull(Thresholds.parse(null))
        assertNull(Thresholds.parse(""))
        assertNull(Thresholds.parse("   "))
        assertNull(Thresholds.parse("Absence"))
        assertNull(
            Thresholds.parse("Acceptable pour les consommateurs et aucun changement anormal"),
        )
        assertNull(Thresholds.parse("50 mg/L"))
        assertNull(Thresholds.parse("mg/L <=50"))
        assertNull(Thresholds.parse("<=abc"))
        // Two bounds on the same side, or crossed bounds.
        assertNull(Thresholds.parse("<=5 et <=9 mg/L"))
        assertNull(Thresholds.parse(">=9 et <=6,5 unité pH"))
        // A comparison left after the unit.
        assertNull(Thresholds.parse("<=5 mg/L ou <=9 mg/L"))
    }
}

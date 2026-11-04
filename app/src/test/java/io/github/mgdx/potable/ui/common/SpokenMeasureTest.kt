package io.github.mgdx.potable.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.sample.ResultValue
import io.github.mgdx.potable.ui.theme.PotableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The spoken forms of values, units and thresholds (TalkBack), in English. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpokenMeasureTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    private val english = Locale.UK

    private fun result(text: String?, number: Double?, unit: String?) =
        spokenResult(resources, text, number, unit, english)

    private fun threshold(text: String) = spokenThreshold(resources, text, english)

    /** Every unit of the table has a spoken form that holds the number, for both forms. */
    @Test
    fun `every unit of the table is spoken with its number`() {
        for (unit in SPOKEN_UNITS.keys) {
            for (number in listOf(1.0, 12.5)) {
                val spoken = spokenQuantity(resources, number, unit, english)
                assert(spoken.contains(if (number == 1.0) "1" else "12.5")) { "$unit: $spoken" }
                assert(!spoken.contains(unit)) { "$unit spelled out in: $spoken" }
            }
        }
    }

    @Test
    fun `published units are spoken in words`() {
        assertEquals("50 milligrams per litre", result("50", 50.0, "mg/L"))
        assertEquals("0.046 micrograms per litre", result("0,046", 0.046, "µg/L"))
        assertEquals("0.12 milligrams of chlorine per litre", result("0,12", 0.12, "mg(Cl2)/L"))
        assertEquals("0 bacteria per 100 millilitres", result("0", 0.0, "n/(100mL)"))
        assertEquals("1 bacterium per 100 millilitres", result("1", 1.0, "n/(100mL)"))
        assertEquals("36.4 French degrees", result("36,4", 36.4, "°f"))
        assertEquals("7.8 pH units", result("7,8", 7.8, "unité pH"))
    }

    @Test
    fun `a number alone, without text, is spoken too`() {
        assertEquals("12.5 milligrams per litre", result(null, 12.5, "mg/L"))
        assertEquals("12.5", result(null, 12.5, "SANS OBJET"))
    }

    @Test
    fun `an unknown unit is said as published`() {
        assertEquals("450 XYZ/cm", result("450", 450.0, "XYZ/cm"))
        assertEquals("less than or equal to 3 XYZ", threshold("<=3 XYZ"))
    }

    @Test
    fun `a result published as a bound is below a threshold`() {
        assertEquals(
            "below the threshold of 0.02 micrograms per litre",
            result("<0,02", 0.02, "µg/L"),
        )
        assertEquals("greater than 80 bacteria per millilitre", result(">80", 80.0, "n/mL"))
    }

    @Test
    fun `texts that are neither a number nor a bound are read as shown`() {
        assertNull(result("N.M.", null, "mg/L"))
        assertNull(result("<SEUIL", 0.0, "µg/L"))
        assertNull(result("Aspect normal", 0.0, "SANS OBJET"))
        assertNull(result(null, null, "mg/L"))
    }

    @Test
    fun `published thresholds are spoken in words`() {
        assertEquals("less than or equal to 50 milligrams per litre", threshold("<=50 mg/L"))
        assertEquals("less than or equal to 0.1 micrograms per litre", threshold("<=0.1 µg/L"))
        assertEquals(
            "less than or equal to 0 bacteria per 100 millilitres",
            threshold("<=0 n/(100mL)"),
        )
        assertEquals(
            "greater than or equal to 6.5 pH units and less than or equal to 9 pH units",
            threshold(">=6,5 et <=9 unité pH"),
        )
        assertEquals("less than 2 milligrams of copper per litre", threshold("<2 mg(Cu)/L"))
        assertEquals("equal to 1", threshold("=1 SANS OBJET"))
    }

    @Test
    fun `a threshold that is not a comparison is left as published`() {
        assertEquals("Absence", threshold("Absence"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `spoken in French`() {
        val french = Locale.FRANCE
        assertEquals(
            "inférieur ou égal à 50 milligrammes par litre",
            spokenThreshold(resources, "<=50 mg/L", french),
        )
        assertEquals(
            "inférieur au seuil de 0,02 microgramme par litre",
            spokenResult(resources, "<0,02", 0.02, "µg/L", french),
        )
        assertEquals(
            "12 bactéries pour 100 millilitres",
            spokenResult(resources, "12", 12.0, "n/(100mL)", french),
        )
        assertEquals("36,4 degrés français", spokenResult(resources, "36,4", 36.4, "°f", french))
    }

    /** TalkBack reads the content description; the screen keeps the published text. */
    @Test
    fun `a result value is shown as published and described in words`() {
        val nitrates = AnalysisResult(
            "1340", "Nitrates (en NO3)", "N", "12,5", 12.5, "mg/L", "<=50 mg/L", null, "L",
        )
        compose.setContent { PotableTheme { ResultValue(nitrates) } }
        compose.onNodeWithText("12,5 mg/L", substring = true)
            .assertTextEquals("12,5 mg/L")
            .assertContentDescriptionEquals("12.5 milligrams per litre")
    }
}

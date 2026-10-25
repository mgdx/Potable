package io.github.mgdx.potable.ui.sample

import io.github.mgdx.potable.core.ParameterFamily
import io.github.mgdx.potable.core.model.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Results with labels and units as Hub'Eau publishes them. */
internal val ecoli = result("1449", "Escherichia coli /100ml - MF", "<1", "n/(100mL)")
internal val enterococci = result("6455", "Entérocoques /100ml-MS", "<1", "n/(100mL)")
internal val nitrates = result("1340", "Nitrates (en NO3)", "4,40", "mg/L", limit = "<=50 mg/L")
internal val freeChlorine = result("1398", "Chlore libre", "0,12", "mg(Cl2)/L")
internal val taste = result("5902", "Saveur (qualitatif)", "Aucun changement anormal", "SANS OBJET")

internal fun result(
    code: String,
    label: String,
    text: String?,
    unit: String?,
    number: Double? = null,
    limit: String? = null,
) = AnalysisResult(code, label, "N", text, number, unit, limit, null)

class SampleResultsTest {
    private val results = listOf(nitrates, ecoli, freeChlorine, enterococci, taste)

    @Test
    fun `a blank query keeps every result`() {
        assertEquals(results, filterResults(results, ""))
        assertEquals(results, filterResults(results, "   "))
    }

    @Test
    fun `the filter ignores case and accents`() {
        assertEquals(listOf(enterococci), filterResults(results, "ENTEROCOQUES"))
        assertEquals(listOf(enterococci), filterResults(results, "entérocoq"))
        assertEquals(listOf(freeChlorine), filterResults(results, "chlore"))
    }

    @Test
    fun `the filter matches anywhere in the label`() {
        assertEquals(listOf(nitrates), filterResults(results, "no3"))
        assertEquals(listOf(ecoli, enterococci), filterResults(results, "100ml"))
    }

    @Test
    fun `nothing matching gives an empty list`() {
        assertEquals(emptyList<AnalysisResult>(), filterResults(results, "glyphosate"))
    }

    @Test
    fun `groups follow the family order and keep the API order inside`() {
        val groups = groupByFamily(results)
        assertEquals(
            listOf(
                FamilyGroup(ParameterFamily.MICROBIOLOGY, listOf(ecoli, enterococci)),
                FamilyGroup(ParameterFamily.PHYSICOCHEMISTRY, listOf(nitrates, taste)),
                FamilyGroup(ParameterFamily.DISINFECTION, listOf(freeChlorine)),
            ),
            groups,
        )
    }

    @Test
    fun `the published text is shown as such, with the unit`() {
        assertEquals("<1 n/(100mL)", valueWithUnit(ecoli, Locale.US))
        assertEquals("4,40 mg/L", valueWithUnit(nitrates, Locale.US))
        val notMeasured = result("1754", "Bioxyde de chlore mg/L ClO2", "N.M.", "mg/L")
        assertEquals("N.M. mg/L", valueWithUnit(notMeasured, Locale.US))
    }

    @Test
    fun `a qualitative result has no unit`() {
        assertEquals("Aucun changement anormal", valueWithUnit(taste, Locale.US))
    }

    @Test
    fun `without text the number is formatted for the locale`() {
        val numeric = result("1340", "Nitrates (en NO3)", null, "mg/L", number = 12.5)
        assertEquals("12.5 mg/L", valueWithUnit(numeric, Locale.US))
        assertEquals("12,5 mg/L", valueWithUnit(numeric, Locale.FRANCE))
    }

    @Test
    fun `no text and no number gives no value`() {
        assertNull(valueWithUnit(result("1340", "Nitrates (en NO3)", null, "mg/L"), Locale.US))
        assertNull(valueWithUnit(result("1340", "Nitrates (en NO3)", " ", "mg/L"), Locale.US))
    }
}

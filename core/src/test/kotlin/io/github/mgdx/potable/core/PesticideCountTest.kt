package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PesticideCountTest {
    private fun result(
        code: String,
        label: String,
        text: String?,
        unit: String = "µg/L",
        place: String? = "L",
    ) = AnalysisResult(
        parameterCode = code,
        parameterLabel = label,
        parameterTypeCode = "N",
        resultText = text,
        resultNumber = text?.removePrefix("<")?.replace(',', '.')?.toDoubleOrNull()
            ?.let { if (text.startsWith("<")) 0.0 else it },
        unit = unit,
        qualityLimit = null,
        qualityReference = null,
        analysisPlace = place,
    )

    /** Results in µg/L of a sample of Amiens (80021), 2026, as published. */
    private val amiens = listOf(
        result("8865", "Chlorothalonil R471811", "0,275"),
        result("6282", "Atrazine et ses métabolites", "0,021"),
        result("7717", "Chlorothalonil R417888", "0,022"),
        result("1107", "Atrazine", "0,008"),
        result("6379", "Chloridazone méthyl desphényl", "0,100"),
        result("6378", "Chloridazone desphényl", "0,397"),
        result("1108", "Atrazine déséthyl", "0,013"),
        result("6276", "Total des pesticides analysés", "0,540"),
        result("1473", "Chlorothalonil", "<0,005"),
        result("1094", "Lambda Cyhalothrine", "<0,005"),
        result("7715", "Chlorothalonil-4-hydroxy", "<0,005"),
        result("6497", "Somme  DDT, DDD, DDE", "<0,030"),
        result("3268", "DDT somme", "<0,015"),
        result("1743", "Endosulfan total", "<0,015"),
        // Other families, in the same unit.
        result("8847", "Somme de 20 substances perfluoroalkylées (PFAS)", "0,005"),
        result("6561", "Acide sulfonique de perfluorooctane (PFOS)", "0,003"),
        result("1382", "Plomb", "1,2"),
        result("1752", "Chlorates en cas de traitement pouvant en générer", "73"),
        result("5426", "Acide dibromoacétique", "0,84"),
        result("1135", "Chloroforme", "0,51"),
        result("1340", "Nitrates (en NO3)", "32", unit = "mg/L"),
    )

    @Test
    fun `pesticides and breakdown products are counted, sums and other families left out`() {
        assertEquals(PesticideCount(analysed = 9, quantified = 6), PesticideCounts.of(amiens))
    }

    @Test
    fun `a parameter measured twice counts once, from the laboratory`() {
        val results = listOf(
            result("1107", "Atrazine", "<0,005", place = "T"),
            result("1107", "Atrazine", "0,008", place = "L"),
        )
        assertEquals(PesticideCount(1, 1), PesticideCounts.of(results))
    }

    @Test
    fun `a text without a number or a zero is not quantified`() {
        val results = listOf(
            result("1107", "Atrazine", "N.M."),
            result("1108", "Atrazine déséthyl", "0"),
            result("1830", "Atrazine déséthyl déisopropyl", null),
        )
        assertEquals(PesticideCount(3, 0), PesticideCounts.of(results))
    }

    @Test
    fun `a sample without pesticides has no count`() {
        assertNull(PesticideCounts.of(emptyList()))
        assertNull(PesticideCounts.of(amiens.filter { it.unit != "µg/L" }))
        assertNull(
            PesticideCounts.of(listOf(result("6276", "Total des pesticides analysés", "0,1"))),
        )
    }
}

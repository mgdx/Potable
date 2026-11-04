package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardnessTest {
    private fun hardness(
        text: String?,
        number: Double?,
        unit: String? = "°f",
        code: String = "1345",
    ) = AnalysisResult(
        parameterCode = code,
        parameterLabel = "Titre hydrotimétrique",
        parameterTypeCode = "N",
        resultText = text,
        resultNumber = number,
        unit = unit,
        qualityLimit = null,
        qualityReference = null,
    )

    @Test
    fun `one french degree is 0,56 german degree and 0,1 mmol per litre`() {
        val equivalents = Hardness.equivalents(1.0)
        assertEquals(0.56, equivalents.germanDegrees, 1e-9)
        assertEquals(0.1, equivalents.millimolesPerLitre, 1e-9)
    }

    @Test
    fun `36,4 french degrees`() {
        val equivalents = Hardness.equivalents(36.4)
        assertEquals(20.384, equivalents.germanDegrees, 1e-9)
        assertEquals(3.64, equivalents.millimolesPerLitre, 1e-9)
    }

    @Test
    fun `zero stays zero`() {
        assertEquals(HardnessEquivalents(0.0, 0.0), Hardness.equivalents(0.0))
    }

    /** As published (fixtures of docs/hubeau-api.md): "12,10", 12.1, "°f". */
    @Test
    fun `published hardness under both codes`() {
        for (code in listOf("1345", "7972")) {
            assertEquals(
                Hardness.equivalents(12.1),
                Hardness.equivalentsOf(hardness("12,10", 12.1, code = code)),
            )
        }
        assertEquals(Hardness.equivalents(12.1), Hardness.equivalentsOf(hardness(null, 12.1)))
        val padded = hardness(" 12.1 ", 12.1, unit = " °f ")
        assertEquals(Hardness.equivalents(12.1), Hardness.equivalentsOf(padded))
    }

    @Test
    fun `censored, missing or placeholder values have no equivalents`() {
        assertNull(Hardness.equivalentsOf(hardness("<1", 1.0)))
        assertNull(Hardness.equivalentsOf(hardness("<SEUIL", 0.0)))
        assertNull(Hardness.equivalentsOf(hardness("N.M.", null)))
        assertNull(Hardness.equivalentsOf(hardness(null, null)))
    }

    @Test
    fun `another unit or parameter has no equivalents`() {
        assertNull(Hardness.equivalentsOf(hardness("12,10", 12.1, unit = "mg/L")))
        assertNull(Hardness.equivalentsOf(hardness("12,10", 12.1, unit = null)))
        assertNull(Hardness.equivalentsOf(hardness("12,10", 12.1, code = "1340")))
    }
}

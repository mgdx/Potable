package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.ui.sample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceSectionTest {
    private val assessed = sample().copy(
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT_UNDER_DEROGATION,
        bacteriologicalReferences = Compliance.NON_COMPLIANT,
        physicochemicalReferences = Compliance.UNKNOWN,
    )

    @Test
    fun `no code S, no not-assessed note`() {
        assertFalse(assessed.hasNotAssessedCompliance())
    }

    @Test
    fun `any one of the four codes S calls for the note`() {
        listOf(
            assessed.copy(bacteriologicalLimits = Compliance.NOT_ASSESSED),
            assessed.copy(physicochemicalLimits = Compliance.NOT_ASSESSED),
            assessed.copy(bacteriologicalReferences = Compliance.NOT_ASSESSED),
            assessed.copy(physicochemicalReferences = Compliance.NOT_ASSESSED),
        ).forEach { assertTrue(it.hasNotAssessedCompliance()) }
    }
}

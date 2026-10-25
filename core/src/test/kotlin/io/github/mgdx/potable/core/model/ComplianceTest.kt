package io.github.mgdx.potable.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ComplianceTest {
    @Test
    fun `documented codes`() {
        assertEquals(Compliance.COMPLIANT, Compliance.fromCode("C"))
        assertEquals(Compliance.NON_COMPLIANT, Compliance.fromCode("N"))
        assertEquals(Compliance.NOT_ASSESSED, Compliance.fromCode("S"))
    }

    @Test
    fun `a derogation is restituted, not folded into compliant`() {
        assertEquals(Compliance.COMPLIANT_UNDER_DEROGATION, Compliance.fromCode("D"))
    }

    @Test
    fun `empty, null and undocumented codes are unknown`() {
        assertEquals(Compliance.UNKNOWN, Compliance.fromCode(null))
        assertEquals(Compliance.UNKNOWN, Compliance.fromCode(""))
        assertEquals(Compliance.UNKNOWN, Compliance.fromCode("c"))
        assertEquals(Compliance.UNKNOWN, Compliance.fromCode("X"))
    }
}

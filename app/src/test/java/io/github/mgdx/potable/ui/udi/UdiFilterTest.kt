package io.github.mgdx.potable.ui.udi

import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.ui.udi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdiFilterTest {
    private val port = udi("013000001", "MARSEILLE PORT AUTONOME")
    private val estaque = Udi("013000002", "MARSEILLE NORD", district = "L'Estaque", year = 2026)
    private val belleDeMai = udi("013000003", "MARSEILLE BELLE-DE-MAI")
    private val units = listOf(port, estaque, belleDeMai)

    @Test
    fun `the filter field is offered above eight units only`() {
        assertFalse(showsUdiFilter(2))
        assertFalse(showsUdiFilter(UDI_FILTER_THRESHOLD))
        assertTrue(showsUdiFilter(UDI_FILTER_THRESHOLD + 1))
        assertTrue(showsUdiFilter(176))
    }

    @Test
    fun `a blank query keeps every unit in its order`() {
        assertEquals(units, filterUdis(units, ""))
        assertEquals(units, filterUdis(units, "   "))
    }

    @Test
    fun `the name matches whatever the case and the accents`() {
        assertEquals(listOf(port), filterUdis(units, "pört"))
        assertEquals(listOf(port), filterUdis(units, "Autonome"))
    }

    @Test
    fun `hyphens and apostrophes match spaces`() {
        assertEquals(listOf(belleDeMai), filterUdis(units, "belle de mai"))
        assertEquals(listOf(estaque), filterUdis(units, "l estaque"))
    }

    @Test
    fun `the district matches too`() {
        assertEquals(listOf(estaque), filterUdis(units, "ESTAQUE"))
    }

    @Test
    fun `nothing matches an unknown text`() {
        assertEquals(emptyList<Udi>(), filterUdis(units, "Toulon"))
    }
}

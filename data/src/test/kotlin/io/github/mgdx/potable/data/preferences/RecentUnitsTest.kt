package io.github.mgdx.potable.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentUnitsTest {
    private fun unit(n: Int, udi: String = "UDI$n", udiName: String = "Unit $n") =
        RecentUnit("C$n", "Commune $n", udi, udiName)

    @Test
    fun `a new unit goes first`() {
        assertEquals(listOf(unit(2), unit(1)), withRecentUnit(listOf(unit(1)), unit(2)))
    }

    @Test
    fun `a unit already listed moves to the top with its new names`() {
        val renamed = unit(1, udiName = "Renamed")
        assertEquals(
            listOf(renamed, unit(2), unit(3)),
            withRecentUnit(listOf(unit(2), unit(1), unit(3)), renamed),
        )
    }

    @Test
    fun `another unit of the same commune is a distinct entry`() {
        val other = RecentUnit("C1", "Commune 1", "OTHER", "Other unit")
        assertEquals(listOf(other, unit(1)), withRecentUnit(listOf(unit(1)), other))
    }

    @Test
    fun `the list is cut to the limit`() {
        val full = (1..AppPreferences.RECENT_LIMIT).map(::unit)
        val result = withRecentUnit(full, unit(99))
        assertEquals(AppPreferences.RECENT_LIMIT, result.size)
        assertEquals(listOf(unit(99)) + full.dropLast(1), result)
    }

    @Test
    fun `removing drops only that commune and unit`() {
        val other = RecentUnit("C1", "Commune 1", "OTHER", "Other unit")
        assertEquals(
            listOf(other, unit(2)),
            withoutRecentUnit(listOf(unit(1), other, unit(2)), "C1", "UDI1"),
        )
    }
}

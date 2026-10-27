package io.github.mgdx.potable.ui.follows

import io.github.mgdx.potable.data.follow.Follow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowFilterTest {
    private val orleans = Follow(1, "45234", "Orléans", "045000474", "ORLEANS", 0)
    private val paris = Follow(2, "75056", "Paris", "075000001", "PARIS EST", 1)
    private val etienne = Follow(3, "42218", "Saint-Étienne", "042000100", "ST-ETIENNE NORD", 2)
    private val all = listOf(orleans, paris, etienne)

    private fun ids(query: String) = filterFollows(all, query).map { it.id }

    @Test
    fun `a blank query keeps every follow in order`() {
        assertEquals(all, filterFollows(all, ""))
        assertEquals(all, filterFollows(all, "   "))
    }

    @Test
    fun `the commune name matches without case nor accents`() {
        assertEquals(listOf(1L), ids("orleans"))
        assertEquals(listOf(1L), ids("ORLÉ"))
        assertEquals(listOf(3L), ids("étienne"))
    }

    @Test
    fun `the unit name matches too`() {
        assertEquals(listOf(2L), ids("est"))
        assertEquals(listOf(3L), ids("nord"))
    }

    @Test
    fun `punctuation and abbreviations are those of the commune search`() {
        assertEquals(listOf(3L), ids("saint etienne"))
        assertEquals(listOf(3L), ids("st-étienne"))
    }

    @Test
    fun `the order of the list is kept`() {
        // "e" is in every name.
        assertEquals(listOf(1L, 2L, 3L), ids("e"))
    }

    @Test
    fun `no match gives an empty list`() {
        assertTrue(filterFollows(all, "lyon").isEmpty())
        assertTrue(filterFollows(emptyList(), "paris").isEmpty())
    }
}

package io.github.mgdx.potable.data.follow

import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomFollowRepositoryTest {
    private val database: PotableDatabase = inMemoryDatabase()
    private val repository = RoomFollowRepository(database)

    @After
    fun close() = database.close()

    private suspend fun follow(commune: String, udi: String = "U$commune") =
        repository.follow(commune, "Commune $commune", udi, "Réseau $udi")

    private suspend fun order() = repository.follows.first().map { it.communeCode }

    private suspend fun primary() = repository.follows.first().single { it.isPrimary }.communeCode

    @Test
    fun `the first follow is primary and the next ones are appended`() = runTest {
        val first = follow("45234")
        val second = follow("75056")

        assertTrue(first.isPrimary)
        assertEquals(0, first.position)
        assertEquals(false, second.isPrimary)
        assertEquals(1, second.position)
        assertEquals(listOf("45234", "75056"), order())
    }

    @Test
    fun `following twice returns the existing follow`() = runTest {
        val first = follow("45234", "045000474")
        follow("75056")
        val again = repository.follow("45234", "Autre nom", "045000474", "Autre réseau")

        assertEquals(first, again)
        assertEquals(2, repository.follows.first().size)
    }

    @Test
    fun `the same commune with another unit is another follow`() = runTest {
        follow("75056", "075000001")
        follow("75056", "075000002")

        assertEquals(2, repository.follows.first().size)
    }

    @Test
    fun `find returns the follow of a unit or null`() = runTest {
        val follow = follow("45234", "045000474")

        assertEquals(follow, repository.find("45234", "045000474"))
        assertNull(repository.find("45234", "other"))
    }

    @Test
    fun `removing the primary makes the first remaining one primary`() = runTest {
        val a = follow("A")
        follow("B")
        follow("C")

        repository.unfollow(a.id)

        assertEquals(listOf("B", "C"), order())
        assertEquals("B", primary())
        assertEquals(listOf(0, 1), repository.follows.first().map { it.position })
    }

    @Test
    fun `removing another follow keeps the primary and closes the gap`() = runTest {
        follow("A")
        val b = follow("B")
        follow("C")

        repository.unfollow(b.id)

        assertEquals(listOf("A", "C"), order())
        assertEquals("A", primary())
        assertEquals(listOf(0, 1), repository.follows.first().map { it.position })
    }

    @Test
    fun `removing the last follow leaves none and the next one is primary again`() = runTest {
        val a = follow("A")
        repository.unfollow(a.id)
        repository.unfollow(a.id)

        assertTrue(repository.follows.first().isEmpty())
        assertTrue(follow("B").isPrimary)
    }

    @Test
    fun `move shifts the others and is clamped`() = runTest {
        val a = follow("A")
        follow("B")
        val c = follow("C")
        follow("D")

        repository.move(a.id, 2)
        assertEquals(listOf("B", "C", "A", "D"), order())

        repository.move(c.id, 99)
        assertEquals(listOf("B", "A", "D", "C"), order())

        repository.move(c.id, -5)
        assertEquals(listOf("C", "B", "A", "D"), order())
        assertEquals(listOf(0, 1, 2, 3), repository.follows.first().map { it.position })
        // Moving does not change which one is primary.
        assertEquals("A", primary())
    }

    @Test
    fun `setPrimary moves the mark and ignores an unknown id`() = runTest {
        follow("A")
        val b = follow("B")

        repository.setPrimary(b.id)
        assertEquals("B", primary())

        repository.setPrimary(12_345)
        assertEquals("B", primary())
    }
}

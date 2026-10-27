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

    private suspend fun names() =
        repository.follows.first().map { "${it.communeName} / ${it.udiName}" }

    @Test
    fun `the follows are sorted by commune name then unit name`() = runTest {
        repository.follow("75056", "Paris", "075000002", "PARIS OUEST")
        repository.follow("91228", "Évry-Courcouronnes", "091000001", "EVRY")
        repository.follow("27229", "Ézy-sur-Eure", "027000001", "EZY")
        repository.follow("75056", "Paris", "075000001", "PARIS EST")
        repository.follow("28134", "Eu", "028000001", "EU")
        repository.follow("45234", "orléans", "045000474", "ORLEANS")

        assertEquals(
            listOf(
                "Eu / EU",
                "Évry-Courcouronnes / EVRY",
                "Ézy-sur-Eure / EZY",
                "orléans / ORLEANS",
                "Paris / PARIS EST",
                "Paris / PARIS OUEST",
            ),
            names(),
        )
    }

    @Test
    fun `unfollowing keeps the others in order`() = runTest {
        follow("C")
        val b = follow("B")
        follow("A")

        repository.unfollow(b.id)

        assertEquals(listOf("A", "C"), order())
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
    fun `removing the last follow leaves none and the next one starts the list again`() = runTest {
        val a = follow("A")
        repository.unfollow(a.id)
        repository.unfollow(a.id)

        assertTrue(repository.follows.first().isEmpty())
        follow("B")
        assertEquals(listOf("B"), order())
    }
}

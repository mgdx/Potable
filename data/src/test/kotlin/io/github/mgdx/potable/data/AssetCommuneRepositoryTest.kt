package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Commune
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AssetCommuneRepositoryTest {
    private val tsv =
        listOf(
            "# potable-communes v1 source=https://geo.api.gouv.fr generated=2026-10-25",
            "45234\tOrléans\t45000,45100\t47.88278\t1.91556\t",
            "75056\tParis\t75001,75002\t48.85889\t2.34694\t",
            "75101\tParis 1er Arrondissement\t75001\t48.86256\t2.33644\t75056",
        ).joinToString(separator = "\n", postfix = "\n")

    private val opens = AtomicInteger()

    private val repository =
        AssetCommuneRepository {
            opens.incrementAndGet()
            tsv.byteInputStream(Charsets.UTF_8)
        }

    @Test
    fun search_delegatesToTheIndex() = runTest {
        val found = repository.search("orleans", 20)

        assertEquals(listOf("45234"), found.map { it.code })
        assertEquals("Orléans", found.single().name)
    }

    @Test
    fun nearest_delegatesToTheIndex() = runTest {
        assertEquals("45234", repository.nearest(47.9, 1.9)?.code)
    }

    @Test
    fun nearest_passesTheMaximumDistance() = runTest {
        // Atlantic Ocean, far from any commune centroid.
        assertNull(repository.nearest(46.0, -10.0, maxDistanceKm = 20.0))
        assertEquals("45234", repository.nearest(47.9, 1.9, maxDistanceKm = 20.0)?.code)
    }

    @Test
    fun preload_readsTheFileOnceAheadOfSearches() = runTest {
        repository.preload()
        assertEquals(1, opens.get())
        repository.search("orleans", 20)
        assertEquals(1, opens.get())
    }

    @Test
    fun byCode_delegatesToTheIndex() = runTest {
        assertEquals("Paris", repository.byCode("75056")?.name)
        assertEquals("75056", repository.byCode("75101")?.hubEauCode)
        assertNull(repository.byCode("99999"))
    }

    @Test
    fun byCode_defaultsToNullInTheInterface() = runTest {
        val fake =
            object : CommuneRepository {
                override suspend fun search(query: String, limit: Int) = emptyList<Commune>()

                override suspend fun nearest(
                    latitude: Double,
                    longitude: Double,
                    maxDistanceKm: Double?,
                ): Commune? = null
            }
        assertNull(fake.byCode("45234"))
    }

    @Test
    fun file_isReadOnceUnderConcurrentCalls() = runTest {
        withContext(Dispatchers.Default) {
            List(16) { i ->
                async {
                    if (i % 2 ==
                        0
                    ) {
                        repository.search("paris", 20)
                    } else {
                        listOfNotNull(repository.nearest(48.8, 2.3))
                    }
                }
            }.awaitAll()
        }
        repository.search("orleans", 20)

        assertEquals(1, opens.get())
    }
}

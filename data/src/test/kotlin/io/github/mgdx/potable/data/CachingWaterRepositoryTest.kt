package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.MutableClock
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import io.github.mgdx.potable.data.local.sample
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CachingWaterRepositoryTest {
    /** Hub'Eau stand-in: answers [samples] per (commune, network), counts the calls. */
    private class FakeNetwork : WaterRepository {
        val samples = mutableMapOf<Pair<String, String?>, Sample?>()
        var failure: DataError? = null
        var calls = 0

        override suspend fun udis(communeCode: String): UdiChoice = UdiChoice.None

        override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? {
            calls++
            failure?.let { throw it }
            return samples[communeCode to networkCode]
        }
    }

    private class FakePreferences(years: Int) : AppPreferences {
        val retention = MutableStateFlow(years)

        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() {}

        override val retentionYears: Flow<Int> = retention

        val forgotten = mutableListOf<Set<String>>()

        override suspend fun forgetRememberedUdis(keep: Set<String>) {
            forgotten += keep
        }
    }

    private val database: PotableDatabase = inMemoryDatabase()
    private val follows = RoomFollowRepository(database)
    private val network = FakeNetwork()
    private val preferences = FakePreferences(AppPreferences.DEFAULT_YEARS)

    // 2026-10-24 10:00 in Paris (summer time, UTC+2).
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z"))
    private val repository = CachingWaterRepository(network, database, preferences, clock)

    @After
    fun close() = database.close()

    private suspend fun followOrleans() = follows.follow("45234", "Orléans", UDI, "ORLEANS")

    private suspend fun cached(commune: String = "45234", udi: String = UDI) =
        repository.cachedLatestSample(commune, udi).first()

    @Test
    fun `udis and latestSample go to the network`() = runTest {
        network.samples["45234" to UDI] = sample("P1", "2026-08-26T12:50:00Z")

        assertEquals(UdiChoice.None, repository.udis("45234"))
        assertEquals("P1", repository.latestSample("45234", UDI)?.code)
        assertEquals(1, network.calls)
        // Not cached: the unit is not followed and this is not a refresh.
        assertNull(cached())
    }

    @Test
    fun `a unit that is not followed is neither fetched nor cached`() = runTest {
        network.samples["45234" to UDI] = sample("P1", "2026-08-26T12:50:00Z")

        assertFalse(repository.refreshFollowed("45234", UDI, force = true))

        assertEquals(0, network.calls)
        assertNull(cached())
    }

    @Test
    fun `nothing is cached before the first refresh`() = runTest {
        followOrleans()

        assertNull(cached())
    }

    @Test
    fun `a refresh caches the sample with its results and the fetch time`() = runTest {
        followOrleans()
        val published = sample("P1", "2026-08-26T12:50:00Z", results = 5)
        network.samples["45234" to UDI] = published

        assertTrue(repository.refreshFollowed("45234", UDI, force = false))

        val cached = cached()!!
        assertEquals(published, cached.sample)
        assertEquals(clock.now, cached.fetchedAt)
    }

    @Test
    fun `a unit without any sample is cached as fetched with no sample`() = runTest {
        followOrleans()

        assertTrue(repository.refreshFollowed("45234", UDI, force = false))

        assertEquals(CachedSample(null, clock.now), cached())
    }

    @Test
    fun `the network is called once a day unless forced`() = runTest {
        followOrleans()
        network.samples["45234" to UDI] = sample("P1", "2026-08-26T12:50:00Z")

        assertTrue(repository.refreshFollowed("45234", UDI, force = false))
        // 23:30 in Paris, still the same day.
        clock.now = Instant.parse("2026-10-24T21:30:00Z")
        assertFalse(repository.refreshFollowed("45234", UDI, force = false))
        assertEquals(1, network.calls)

        assertTrue(repository.refreshFollowed("45234", UDI, force = true))
        assertEquals(2, network.calls)
        assertEquals(clock.now, cached()!!.fetchedAt)

        // 00:30 in Paris the next day, although still 2026-10-24 in UTC.
        clock.now = Instant.parse("2026-10-24T22:30:00Z")
        assertTrue(repository.refreshFollowed("45234", UDI, force = false))
        assertEquals(3, network.calls)
    }

    @Test
    fun `a failed refresh keeps the cache and is tried again`() = runTest {
        followOrleans()
        network.samples["45234" to UDI] = sample("P1", "2026-08-26T12:50:00Z")
        repository.refreshFollowed("45234", UDI, force = false)
        val before = cached()

        clock.now = Instant.parse("2026-10-26T08:00:00Z")
        network.failure = DataError.Network(IOException("offline"))
        try {
            repository.refreshFollowed("45234", UDI, force = false)
            fail("DataError expected")
        } catch (expected: DataError.Network) {
            // Propagated to the caller, which shows the cache with its banner.
        }

        assertEquals(before, cached())
        network.failure = null
        assertTrue(repository.refreshFollowed("45234", UDI, force = false))
        assertEquals(3, network.calls)
    }

    @Test
    fun `a new sample becomes the latest and refetching one does not duplicate it`() = runTest {
        followOrleans()
        network.samples["45234" to UDI] = sample("P1", "2026-08-12T10:54:00Z")
        repository.refreshFollowed("45234", UDI, force = false)

        network.samples["45234" to UDI] = sample("P2", "2026-08-26T12:50:00Z", results = 3)
        repository.refreshFollowed("45234", UDI, force = true)
        repository.refreshFollowed("45234", UDI, force = true)

        val latest = cached()!!.sample!!
        assertEquals("P2", latest.code)
        assertEquals(3, latest.results.size)
        assertEquals(2, database.cache().sampleCount())
    }

    @Test
    fun `the retention purge keeps the newest sample of every unit`() = runTest {
        preferences.retention.value = 1
        followOrleans()
        follows.follow("75056", "Paris", "075000001", "CENTRE")
        // Paris: a single sample, older than the retention window.
        network.samples["75056" to "075000001"] = sample("PARIS", "2024-03-01T10:00:00Z")
        repository.refreshFollowed("75056", "075000001", force = false)
        // Orléans: three samples, two of them older than one year.
        for ((code, date) in listOf(
            "OLD1" to "2024-01-10T10:00:00Z",
            "OLD2" to "2025-02-10T10:00:00Z",
            "RECENT" to "2026-01-10T10:00:00Z",
        )) {
            network.samples["45234" to UDI] = sample(code, date)
            repository.refreshFollowed("45234", UDI, force = true)
        }
        // A last refresh that brings nothing new still purges.
        network.samples["45234" to UDI] = sample("OLD1", "2024-01-10T10:00:00Z")
        repository.refreshFollowed("45234", UDI, force = true)

        // RECENT stays as the newest; OLD1, written again, is purged.
        assertEquals("RECENT", cached()!!.sample!!.code)
        assertEquals("PARIS", cached("75056", "075000001")!!.sample!!.code)
        assertEquals(2, database.cache().sampleCount())
        // The results of the purged samples went with them (cascade).
        assertEquals(4, resultCount())
    }

    @Test
    fun `a shorter retention purges the samples at once, without a refresh`() = runTest {
        followOrleans()
        follows.follow("75056", "Paris", "075000001", "CENTRE")
        network.samples["75056" to "075000001"] = sample("PARIS", "2024-03-01T10:00:00Z")
        repository.refreshFollowed("75056", "075000001", force = false)
        for ((code, date) in listOf(
            "OLD1" to "2024-01-10T10:00:00Z",
            "OLD2" to "2025-02-10T10:00:00Z",
            "RECENT" to "2026-01-10T10:00:00Z",
        )) {
            network.samples["45234" to UDI] = sample(code, date)
            repository.refreshFollowed("45234", UDI, force = true)
        }
        // Three years: everything is kept.
        assertEquals(4, database.cache().sampleCount())
        val calls = network.calls

        preferences.retention.value = 1
        repository.purgeOlderThanRetention()

        // OLD1 and OLD2 go; Paris keeps its only, older, sample.
        assertEquals(2, database.cache().sampleCount())
        assertEquals("RECENT", cached()!!.sample!!.code)
        assertEquals("PARIS", cached("75056", "075000001")!!.sample!!.code)
        assertEquals(calls, network.calls)
    }

    private fun resultCount(): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM resultat").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `purgeCache empties the cache and keeps the follows`() = runTest {
        followOrleans()
        network.samples["45234" to UDI] = sample("P1", "2026-08-26T12:50:00Z")
        repository.refreshFollowed("45234", UDI, force = false)

        repository.purgeCache()

        assertNull(cached())
        assertEquals(0, database.cache().sampleCount())
        assertEquals(0, resultCount())
        assertEquals(1, follows.follows.first().size)
        // The sync state is gone too: the next display fetches again.
        assertTrue(repository.refreshFollowed("45234", UDI, force = false))
        assertEquals(2, network.calls)
    }

    @Test
    fun `purgeCache forgets the units remembered for communes not followed`() = runTest {
        followOrleans()

        repository.purgeCache()

        assertEquals(listOf(setOf("45234")), preferences.forgotten)
    }

    @Test
    fun `unfollowing deletes the unit's cache only`() = runTest {
        val orleans = followOrleans()
        follows.follow("45234", "Orléans", "045000999", "ORLEANS SUD")
        // The same sample published for both units of the commune.
        network.samples["45234" to UDI] = sample("SHARED", "2026-08-26T12:50:00Z")
        network.samples["45234" to "045000999"] = sample("SHARED", "2026-08-26T12:50:00Z")
        repository.refreshFollowed("45234", UDI, force = false)
        repository.refreshFollowed("45234", "045000999", force = false)

        follows.unfollow(orleans.id)

        assertNull(cached())
        assertEquals("SHARED", cached("45234", "045000999")!!.sample!!.code)
        assertEquals(1, database.cache().sampleCount())
        assertEquals(2, resultCount())
        // Following it again starts from an empty cache.
        followOrleans()
        assertNull(cached())
    }

    @Test
    fun `a refresh does not touch the last seen sample date`() = runTest {
        followOrleans()
        val seen = Instant.parse("2026-08-12T10:54:00Z")
        repository.setLastSeenSampleDate("45234", UDI, seen)
        network.samples["45234" to UDI] = sample("P2", "2026-08-26T12:50:00Z")

        repository.refreshFollowed("45234", UDI, force = false)

        assertEquals(seen, repository.lastSeenSampleDate("45234", UDI))
        // Writing the date alone does not count as a fetch.
        repository.setLastSeenSampleDate("45234", UDI, null)
        assertNull(repository.lastSeenSampleDate("45234", UDI))
        assertEquals(clock.now, cached()!!.fetchedAt)
    }

    @Test
    fun `the last seen date is not recorded for a unit that is not followed`() = runTest {
        repository.setLastSeenSampleDate("45234", UDI, Instant.parse("2026-08-12T10:54:00Z"))

        assertNull(repository.lastSeenSampleDate("45234", UDI))
        assertNull(cached())
    }

    private companion object {
        const val UDI = "045000474"
    }
}

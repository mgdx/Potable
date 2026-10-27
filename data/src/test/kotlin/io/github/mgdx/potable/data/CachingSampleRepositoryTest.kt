package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
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
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CachingSampleRepositoryTest {
    /** Hub'Eau stand-in for one sample at a time; counts the calls. */
    private class FakeSamples : SampleRepository {
        val samples = mutableMapOf<String, Sample>()
        var failure: DataError? = null
        var calls = 0

        override suspend fun sample(
            communeCode: String,
            udiCode: String,
            sampleCode: String,
        ): Sample? {
            calls++
            failure?.let { throw it }
            return samples[sampleCode]
        }
    }

    /** Hub'Eau stand-in for the latest samples. */
    private class FakeLatest : WaterRepository {
        var latest: List<Sample> = emptyList()

        override suspend fun udis(communeCode: String): UdiChoice = UdiChoice.None

        override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? =
            latest.firstOrNull()

        override suspend fun latestSamples(communeCode: String, networkCode: String?) = latest
    }

    private class FakePreferences : AppPreferences {
        val retention = MutableStateFlow(AppPreferences.DEFAULT_YEARS)

        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() {}

        override val retentionYears: Flow<Int> = retention
    }

    private val database: PotableDatabase = inMemoryDatabase()
    private val network = FakeSamples()
    private val latest = FakeLatest()
    private val preferences = FakePreferences()
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z"))
    private val repository = CachingSampleRepository(network, database, preferences, clock)
    private val water = CachingWaterRepository(latest, database, preferences, clock)

    @After
    fun close() = database.close()

    private suspend fun report(code: String) = repository.sample("45234", UDI, code)

    @Test
    fun `a sample is fetched once, then read from the cache with its results`() = runTest {
        val old = sample("OLD", "2025-03-01T09:00:00Z", results = 4)
        network.samples["OLD"] = old

        assertEquals(old, report("OLD"))
        network.failure = DataError.Network(IOException("offline"))
        assertEquals(old, report("OLD"))

        assertEquals(1, network.calls)
    }

    @Test
    fun `a latest sample already cached by a refresh needs no request`() = runTest {
        val newest = sample("NEW", "2026-08-26T12:50:00Z", results = 3)
        latest.latest = listOf(newest)
        water.refreshCache("45234", UDI, force = false)

        assertEquals(newest, report("NEW"))
        assertEquals(0, network.calls)
    }

    @Test
    fun `a sample Hub'Eau does not have, or a failure, caches nothing`() = runTest {
        assertNull(report("NONE"))
        network.failure = DataError.Http(503)
        try {
            report("NONE")
            fail("DataError expected")
        } catch (_: DataError.Http) {
            // Expected.
        }
        assertEquals(0, database.cache().sampleCount())
    }

    @Test
    fun `a sample from the history is never taken for a latest one`() = runTest {
        latest.latest = listOf(sample("NEW", "2026-08-26T12:50:00Z"))
        water.refreshCache("45234", UDI, force = false)
        network.samples["OLD"] = sample("OLD", "2025-03-01T09:00:00Z")
        // Even newer than the cached latest (a stale cache): still not one.
        network.samples["NEWER"] = sample("NEWER", "2026-09-30T08:00:00Z")
        // On the same day as the cached latest: not one either.
        network.samples["SAMEDAY"] = sample("SAMEDAY", "2026-08-26T08:00:00Z")

        report("OLD")
        report("NEWER")
        report("SAMEDAY")

        val cached = water.cachedLatestSample("45234", UDI).first()!!
        assertEquals(listOf("NEW"), cached.samples.map { it.code })
        assertEquals(4, database.cache().sampleCount())
    }

    @Test
    fun `a unit with history samples only has no latest sample`() = runTest {
        latest.latest = emptyList()
        water.refreshCache("45234", UDI, force = false)
        network.samples["OLD"] = sample("OLD", "2025-03-01T09:00:00Z")

        report("OLD")

        assertEquals(emptyList<Sample>(), water.cachedLatestSample("45234", UDI).first()!!.samples)
    }

    @Test
    fun `kept samples go with the purges and are listed by commune`() = runTest {
        network.samples["OLD"] = sample("OLD", "2025-03-01T09:00:00Z")
        report("OLD")
        assertEquals(
            listOf(CachedCommune("45234", setOf(UDI), null)),
            water.cachedCommunes().first().map { it.copy(sizeBytes = 0) },
        )

        water.purgeCommune("45234")
        assertEquals(0, database.cache().sampleCount())
        report("OLD")
        assertEquals(2, network.calls)

        water.purgeCache()
        assertEquals(0, database.cache().sampleCount())
    }

    @Test
    fun `kept samples follow the retention, the latest ones stay`() = runTest {
        latest.latest = listOf(sample("NEW", "2024-03-01T09:00:00Z"))
        water.refreshCache("45234", UDI, force = false)
        network.samples["OLD"] = sample("OLD", "2023-03-01T09:00:00Z")
        network.samples["RECENT"] = sample("RECENT", "2026-03-01T09:00:00Z")
        preferences.retention.value = 1

        // Returned, but past the retention: not kept.
        assertEquals("OLD", report("OLD")?.code)
        report("RECENT")

        // The latest stays although older than a year; RECENT is within it.
        assertEquals(2, database.cache().sampleCount())
        assertEquals("NEW", water.cachedLatestSample("45234", UDI).first()!!.sample!!.code)
        // Another unit with history samples only is purged as well.
        network.samples["PARIS"] = sample("PARIS", "2023-03-01T09:00:00Z")
        repository.sample("75056", "075000001", "PARIS")
        water.purgeOlderThanRetention()
        assertEquals(2, database.cache().sampleCount())
    }

    @Test
    fun `a purge made while the request runs is not undone by it`() = runTest {
        val purging = object : SampleRepository {
            override suspend fun sample(
                communeCode: String,
                udiCode: String,
                sampleCode: String,
            ): Sample {
                water.purgeCommune(communeCode)
                return sample(sampleCode, "2026-03-01T09:00:00Z")
            }
        }
        val racing = CachingSampleRepository(purging, database, preferences, clock)

        assertEquals("OLD", racing.sample("45234", UDI, "OLD")?.code)
        assertEquals(0, database.cache().sampleCount())
    }

    private companion object {
        const val UDI = "045000474"
    }
}

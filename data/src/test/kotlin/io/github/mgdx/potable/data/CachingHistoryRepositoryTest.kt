package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.Thresholds
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.Threshold
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.MutableClock
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CachingHistoryRepositoryTest {
    /** Hub'Eau stand-in: answers from its maps, filtered on `since`, counts the calls. */
    private class FakeNetwork : HistoryRepository {
        var points = mutableListOf<SeriesPoint>()
        var label: String? = "Nitrates (en NO3)"
        var limitText: String? = "<=50 mg/L"
        val samples = mutableListOf<SampleSummary>()
        var failure: DataError? = null
        var seriesCalls = 0
        var timelineCalls = 0

        override suspend fun parameterSeries(
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
            since: LocalDate,
            force: Boolean,
        ): ParameterSeries? {
            seriesCalls++
            failure?.let { throw it }
            val kept = points.filter { !it.date.isBefore(since.start()) }
            val name = label ?: return null
            if (kept.isEmpty()) return null
            return ParameterSeries(
                parameterCode = parameterCodes.first(),
                label = name,
                unit = "mg/L",
                limitText = limitText,
                referenceText = null,
                limit = Thresholds.parse(limitText),
                reference = null,
                points = kept.sortedBy { it.date },
            )
        }

        override suspend fun sampleTimeline(
            communeCode: String,
            udiCode: String,
            since: LocalDate,
            force: Boolean,
        ): List<SampleSummary> {
            timelineCalls++
            failure?.let { throw it }
            val from = since.start()
            return samples.filter { !it.date.isBefore(from) }.sortedByDescending { it.date }
        }
    }

    private class FakePreferences(years: Int) : AppPreferences {
        val retention = MutableStateFlow(years)

        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() {}

        override val retentionYears: Flow<Int> = retention
    }

    private val database: PotableDatabase = inMemoryDatabase()
    private val follows = RoomFollowRepository(database)
    private val network = FakeNetwork()
    private val preferences = FakePreferences(10)

    // 2026-10-24 10:00 in Paris (summer time, UTC+2).
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z"))
    private val repository = CachingHistoryRepository(network, database, preferences, clock)
    private val water =
        CachingWaterRepository(
            object : WaterRepository {
                override suspend fun udis(communeCode: String) = error("unused")

                override suspend fun latestSample(communeCode: String, networkCode: String?) =
                    error("unused")
            },
            database,
            preferences,
            clock,
        )

    private val threeYears = LocalDate.parse("2023-10-24")

    @After
    fun close() = database.close()

    private suspend fun followOrleans() = follows.follow("45234", "Orléans", UDI, "ORLEANS")

    private suspend fun series(since: LocalDate = threeYears, force: Boolean = false) =
        repository.parameterSeries("45234", UDI, setOf("1340"), since, force)

    private suspend fun timeline(since: LocalDate = threeYears, force: Boolean = false) =
        repository.sampleTimeline("45234", UDI, since, force)

    private fun point(code: String, date: String, value: Double = 4.4) = SeriesPoint(
        date = Instant.parse(date),
        sampleCode = code,
        value = value,
        text = value.toString().replace('.', ','),
        censored = false,
    )

    private fun summary(code: String, date: String) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = "Eau conforme.",
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT_UNDER_DEROGATION,
        bacteriologicalReferences = Compliance.NOT_ASSESSED,
        physicochemicalReferences = Compliance.UNKNOWN,
    )

    private fun givenHistory() {
        network.points += point("P1", "2024-03-01T09:00:00Z", 3.0)
        network.points += point("P2", "2026-08-26T12:50:00Z", 4.4)
        network.samples += summary("S1", "2024-03-01T09:00:00Z")
        network.samples += summary("S2", "2026-08-26T12:50:00Z")
    }

    @Test
    fun `a unit that is not followed always goes to the network and is never cached`() = runTest {
        givenHistory()

        assertEquals(2, series()?.points?.size)
        assertEquals(2, series()?.points?.size)
        assertEquals(2, timeline().size)
        assertEquals(2, timeline().size)

        assertEquals(2, network.seriesCalls)
        assertEquals(2, network.timelineCalls)
        assertEquals(0, database.history().pointCount())
        assertEquals(0, database.history().timelineCount())
    }

    @Test
    fun `a followed unit is fetched once a day, then read from the cache`() = runTest {
        followOrleans()
        givenHistory()

        val fetched = series()
        val cached = series()

        assertEquals(1, network.seriesCalls)
        assertEquals(fetched, cached)
        assertEquals(listOf("P1", "P2"), cached!!.points.map { it.sampleCode })
        assertEquals("Nitrates (en NO3)", cached.label)
        assertEquals("<=50 mg/L", cached.limitText)
        // The cache parses the bound again from the published text.
        assertEquals(Threshold(null, 50.0, "mg/L"), cached.limit)

        val timeline = timeline()
        assertEquals(timeline, timeline())
        assertEquals(1, network.timelineCalls)
        assertEquals(listOf("S2", "S1"), timeline.map { it.code })
        assertEquals(Compliance.COMPLIANT_UNDER_DEROGATION, timeline.first().physicochemicalLimits)
        assertEquals(Compliance.UNKNOWN, timeline.first().physicochemicalReferences)
    }

    @Test
    fun `the next day in Paris, or force, goes to the network again`() = runTest {
        followOrleans()
        givenHistory()
        series()
        timeline()

        // 23:59 in Paris, still the same day.
        clock.now = Instant.parse("2026-10-24T21:59:00Z")
        series()
        timeline()
        assertEquals(1, network.seriesCalls)
        assertEquals(1, network.timelineCalls)

        // 00:01 in Paris, the 25th, although still the 24th in UTC.
        clock.now = Instant.parse("2026-10-24T22:01:00Z")
        series()
        timeline()
        assertEquals(2, network.seriesCalls)
        assertEquals(2, network.timelineCalls)

        series(force = true)
        timeline(force = true)
        assertEquals(3, network.seriesCalls)
        assertEquals(3, network.timelineCalls)
    }

    @Test
    fun `a window going back before the cache goes to the network, a shorter one does not`() =
        runTest {
            followOrleans()
            givenHistory()
            network.points += point("P0", "2018-05-01T09:00:00Z", 1.0)
            network.samples += summary("S0", "2018-05-01T09:00:00Z")

            assertEquals(2, series()?.points?.size)
            assertEquals(2, timeline().size)

            val oneYear = LocalDate.parse("2025-10-24")
            assertEquals(listOf("P2"), series(oneYear)?.points?.map { it.sampleCode })
            assertEquals(listOf("S2"), timeline(oneYear).map { it.code })
            assertEquals(1, network.seriesCalls)
            assertEquals(1, network.timelineCalls)

            val tenYears = LocalDate.parse("2016-10-24")
            assertEquals(3, series(tenYears)?.points?.size)
            assertEquals(3, timeline(tenYears).size)
            assertEquals(2, network.seriesCalls)
            assertEquals(2, network.timelineCalls)

            // Now covered: the three-year window comes from the cache.
            assertEquals(2, series()?.points?.size)
            assertEquals(2, network.seriesCalls)
        }

    @Test
    fun `a shorter fetch keeps the older cached points`() = runTest {
        followOrleans()
        givenHistory()
        series()
        timeline()

        network.points.removeAll { it.sampleCode == "P2" }
        network.points += point("P2", "2026-08-26T12:50:00Z", 9.9)
        network.points += point("P3", "2026-10-01T08:00:00Z", 5.0)
        network.samples += summary("S3", "2026-10-01T08:00:00Z")
        val oneYear = LocalDate.parse("2025-10-24")
        series(oneYear, force = true)
        timeline(oneYear, force = true)

        // The older point is kept, the newer ones are replaced.
        val cached = series()!!
        assertEquals(listOf("P1", "P2", "P3"), cached.points.map { it.sampleCode })
        assertEquals(listOf(3.0, 9.9, 5.0), cached.points.map { it.value })
        assertEquals(listOf("S3", "S2", "S1"), timeline().map { it.code })
        assertEquals(2, network.seriesCalls)
        assertEquals(2, network.timelineCalls)
    }

    @Test
    fun `a fetch that does not join the cache drops the older points`() = runTest {
        followOrleans()
        givenHistory()
        series()
        timeline()

        // Two years later, a one-year window: the cache stops before it starts.
        clock.now = Instant.parse("2028-10-24T08:00:00Z")
        network.points += point("P3", "2028-01-01T08:00:00Z", 5.0)
        network.samples += summary("S3", "2028-01-01T08:00:00Z")
        val oneYear = LocalDate.parse("2027-10-24")
        assertEquals(listOf("P3"), series(oneYear)?.points?.map { it.sampleCode })
        assertEquals(listOf("S3"), timeline(oneYear).map { it.code })

        assertEquals(1, database.history().pointCount())
        assertEquals(1, database.history().timelineCount())
        // The three-year window is not covered any more: back to the network.
        series(LocalDate.parse("2025-10-24"))
        assertEquals(3, network.seriesCalls)
    }

    @Test
    fun `a network error returns the cache`() = runTest {
        followOrleans()
        givenHistory()
        val fetched = series()
        val fetchedTimeline = timeline()

        network.failure = DataError.Network(IOException("offline"))
        assertEquals(fetched, series(force = true))
        assertEquals(fetchedTimeline, timeline(force = true))
        // Even for a window the cache does not fully cover: what is there.
        assertEquals(fetched, series(LocalDate.parse("2016-10-24")))
        assertEquals(2, timeline(LocalDate.parse("2016-10-24")).size)
    }

    @Test
    fun `a network error without cache is thrown`() = runTest {
        followOrleans()
        network.failure = DataError.Http(503)

        try {
            series()
            fail("expected DataError")
        } catch (e: DataError.Http) {
            assertEquals(503, e.code)
        }
        try {
            timeline()
            fail("expected DataError")
        } catch (_: DataError.Http) {
            // Expected.
        }
        // Not followed: thrown as well.
        follows.unfollow(follows.find("45234", UDI)!!.id)
        try {
            series()
            fail("expected DataError")
        } catch (_: DataError.Http) {
            // Expected.
        }
    }

    @Test
    fun `no series at Hub'Eau is cached as such`() = runTest {
        followOrleans()

        assertNull(series())
        assertNull(series())
        assertEquals(emptyList<SampleSummary>(), timeline())
        assertEquals(emptyList<SampleSummary>(), timeline())

        assertEquals(1, network.seriesCalls)
        assertEquals(1, network.timelineCalls)
    }

    @Test
    fun `unfollowing deletes the unit's history, and only it`() = runTest {
        val orleans = followOrleans()
        follows.follow("75056", "Paris", "075000221", "CENTRE")
        givenHistory()
        series()
        timeline()
        repository.parameterSeries("75056", "075000221", setOf("1340"), threeYears)
        repository.sampleTimeline("75056", "075000221", threeYears)
        assertEquals(4, database.history().pointCount())
        assertEquals(4, database.history().timelineCount())

        follows.unfollow(orleans.id)

        assertEquals(2, database.history().pointCount())
        assertEquals(2, database.history().timelineCount())
        assertNull(database.history().series("45234", UDI, "1340"))
        assertNull(database.history().timelineSync("45234", UDI))
    }

    @Test
    fun `purgeCache deletes every history but keeps the follows`() = runTest {
        followOrleans()
        givenHistory()
        series()
        timeline()

        water.purgeCache()

        assertEquals(0, database.history().pointCount())
        assertEquals(0, database.history().timelineCount())
        assertNull(database.history().series("45234", UDI, "1340"))
        assertNull(database.history().timelineSync("45234", UDI))
        // Followed still: the next read fetches again.
        series()
        timeline()
        assertEquals(2, network.seriesCalls)
        assertEquals(2, network.timelineCalls)
    }

    @Test
    fun `a shorter retention purges the history at once, without a fetch`() = runTest {
        followOrleans()
        givenHistory()
        series()
        timeline()
        assertEquals(2, database.history().pointCount())

        preferences.retention.value = 1
        water.purgeOlderThanRetention()

        // Same purge as after a fetch: one year kept, the coverage clamped.
        assertEquals(1, database.history().pointCount())
        assertEquals(1, database.history().timelineCount())
        assertEquals(
            LocalDate.parse("2025-10-24").toEpochDay(),
            database.history().series("45234", UDI, "1340")?.depuis,
        )
        assertEquals(1, network.seriesCalls)
        assertEquals(1, network.timelineCalls)
    }

    @Test
    fun `the retention purge applies to the history`() = runTest {
        followOrleans()
        givenHistory()
        preferences.retention.value = 1
        val tenYears = LocalDate.parse("2016-10-24")

        // The fetch itself is returned whole...
        assertEquals(2, series(tenYears)?.points?.size)
        assertEquals(2, timeline(tenYears).size)
        // ...but only one year is kept, and the coverage says so.
        assertEquals(1, database.history().pointCount())
        assertEquals(1, database.history().timelineCount())
        assertEquals(
            LocalDate.parse("2025-10-24").toEpochDay(),
            database.history().series("45234", UDI, "1340")?.depuis,
        )
        val oneYear = LocalDate.parse("2025-10-24")
        assertEquals(listOf("P2"), series(oneYear)?.points?.map { it.sampleCode })
        assertEquals(1, network.seriesCalls)
        // A window beyond the retention cannot come from the cache.
        series(tenYears)
        assertEquals(2, network.seriesCalls)
    }

    @Test
    fun `a unit unfollowed during the fetch is not cached`() = runTest {
        val orleans = followOrleans()
        givenHistory()
        val unfollowing =
            object : HistoryRepository by network {
                override suspend fun parameterSeries(
                    communeCode: String,
                    udiCode: String,
                    parameterCodes: Set<String>,
                    since: LocalDate,
                    force: Boolean,
                ): ParameterSeries? {
                    follows.unfollow(orleans.id)
                    return network.parameterSeries(
                        communeCode,
                        udiCode,
                        parameterCodes,
                        since,
                        force,
                    )
                }
            }
        val racing = CachingHistoryRepository(unfollowing, database, preferences, clock)

        assertEquals(
            2,
            racing.parameterSeries("45234", UDI, setOf("1340"), threeYears)?.points?.size,
        )
        assertEquals(0, database.history().pointCount())
        assertNull(database.history().series("45234", UDI, "1340"))
    }

    @Test
    fun `the cache key is the set of codes, in any order`() = runTest {
        followOrleans()
        givenHistory()

        repository.parameterSeries("45234", UDI, setOf("7972", "1345"), threeYears)
        repository.parameterSeries("45234", UDI, setOf("1345", "7972"), threeYears)
        repository.parameterSeries("45234", UDI, setOf("1345"), threeYears)

        assertEquals(2, network.seriesCalls)
    }

    private companion object {
        const val UDI = "045000474"

        fun LocalDate.start(): Instant = atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
    }
}

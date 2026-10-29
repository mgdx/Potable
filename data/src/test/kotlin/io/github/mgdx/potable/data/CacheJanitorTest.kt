package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.MutableClock
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.data.preferences.withRecentUnit
import io.github.mgdx.potable.data.preferences.withoutRecentUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import io.github.mgdx.potable.data.local.sample as testSample

/**
 * The cache keeps only the units followed or among the recent ones (SPEC
 * §7): [WaterRepository.pruneUnreferenced] and the [CacheJanitor] that runs
 * it.
 */
@RunWith(RobolectricTestRunner::class)
class CacheJanitorTest {
    /** The recent list, kept as the DataStore implementation keeps it. */
    private class FakePreferences : AppPreferences {
        val recent = MutableStateFlow<List<RecentUnit>>(emptyList())

        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() {}

        override val recentUnits: Flow<List<RecentUnit>> = recent

        override suspend fun addRecentUnit(unit: RecentUnit) {
            recent.value = withRecentUnit(recent.value, unit)
        }

        override suspend fun removeRecentUnit(communeCode: String, udiCode: String) {
            recent.value = withoutRecentUnit(recent.value, communeCode, udiCode)
        }
    }

    /**
     * Hub'Eau stand-in for the three caches; [during] runs while a request
     * is in flight, before it answers.
     */
    private class FakeNetwork :
        WaterRepository,
        HistoryRepository,
        SampleRepository {
        var during: suspend () -> Unit = {}

        override suspend fun udis(communeCode: String): UdiChoice = UdiChoice.None

        override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? {
            during()
            return testSample("L-$networkCode", "2026-08-26T12:50:00Z", communeCode)
        }

        override suspend fun parameterSeries(
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
            since: LocalDate,
            force: Boolean,
        ): ParameterSeries? {
            during()
            return ParameterSeries(
                parameterCode = parameterCodes.first(),
                label = "Nitrates (en NO3)",
                unit = "mg/L",
                limitText = null,
                referenceText = null,
                limit = null,
                reference = null,
                points = listOf(
                    SeriesPoint(Instant.parse("2026-08-26T12:50:00Z"), "S", 4.4, "4,4", false),
                ),
            )
        }

        override suspend fun sampleTimeline(
            communeCode: String,
            udiCode: String,
            since: LocalDate,
            force: Boolean,
        ): List<SampleSummary> {
            during()
            return listOf(
                SampleSummary(
                    code = "T",
                    date = Instant.parse("2026-08-26T12:50:00Z"),
                    conclusion = "Eau conforme.",
                    bacteriologicalLimits = Compliance.COMPLIANT,
                    physicochemicalLimits = Compliance.COMPLIANT,
                    bacteriologicalReferences = Compliance.COMPLIANT,
                    physicochemicalReferences = Compliance.COMPLIANT,
                ),
            )
        }

        override suspend fun parameterSeriesBefore(
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
            since: LocalDate,
            until: LocalDate,
        ): ParameterSeries? = null

        override suspend fun sampleTimelineBefore(
            communeCode: String,
            udiCode: String,
            since: LocalDate,
            until: LocalDate,
        ): List<SampleSummary> = emptyList()

        override suspend fun sample(
            communeCode: String,
            udiCode: String,
            sampleCode: String,
        ): Sample? {
            during()
            return testSample(sampleCode, "2025-03-01T09:00:00Z", communeCode)
        }
    }

    private val database: PotableDatabase = inMemoryDatabase()
    private val follows = RoomFollowRepository(database)
    private val preferences = FakePreferences()
    private val network = FakeNetwork()

    // 2026-10-24 10:00 in Paris.
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z"))
    private val water = CachingWaterRepository(network, database, preferences, clock)
    private val history = CachingHistoryRepository(network, database, preferences, clock)
    private val samples = CachingSampleRepository(network, database, preferences, clock)
    private val janitor = CacheJanitor(follows, preferences, water)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val orleans = RecentUnit("45234", "Orléans", "045000474", "ORLEANS")
    private val orleansSud = RecentUnit("45234", "Orléans", "045000999", "ORLEANS SUD")
    private val paris = RecentUnit("75056", "Paris", "075000221", "PARIS")
    private val tours = RecentUnit("37261", "Tours", "037000001", "TOURS")

    @After
    fun close() {
        scope.cancel()
        database.close()
    }

    private val RecentUnit.key get() = UnitKey(communeCode, udiCode)

    /** Fills every cache table of [unit]: latest sample, report, series, timeline. */
    private suspend fun fill(unit: RecentUnit) {
        water.refreshCache(unit.communeCode, unit.udiCode, force = true)
        samples.sample(unit.communeCode, unit.udiCode, "R-${unit.udiCode}")
        history.parameterSeries(unit.communeCode, unit.udiCode, setOf("1340"), YEAR_AGO, true)
        history.sampleTimeline(unit.communeCode, unit.udiCode, YEAR_AGO, force = true)
    }

    private suspend fun cachedUnits(): Set<UnitKey> = database.cache().cachedUnitKeys().toSet()

    /** Whether every cache table holds rows of [unit] (as [fill] leaves them). */
    private suspend fun fullyCached(unit: RecentUnit): Boolean {
        val (commune, udi) = unit.communeCode to unit.udiCode
        val cache = database.cache()
        val h = database.history()
        return cache.sync(commune, udi)?.lastSuccess != null &&
            cache.sample(commune, udi, "L-$udi") != null &&
            cache.results(commune, udi, "R-$udi").isNotEmpty() &&
            h.series(commune, udi, "1340") != null &&
            h.points(commune, udi, "1340", Long.MIN_VALUE).isNotEmpty() &&
            h.timelineSync(commune, udi) != null &&
            h.timeline(commune, udi, Long.MIN_VALUE).isNotEmpty()
    }

    private suspend fun follow(unit: RecentUnit) =
        follows.follow(unit.communeCode, unit.communeName, unit.udiCode, unit.udiName)

    /** Waits, in real time, for the janitor's work: Room answers on its own threads. */
    private fun eventually(condition: suspend () -> Boolean) = runBlocking {
        withTimeout(TIMEOUT_MS) { while (!condition()) delay(POLL_MS) }
    }

    @Test
    fun `a prune deletes every table of an unreferenced unit and keeps the others`() = runTest {
        follow(paris)
        preferences.addRecentUnit(orleans)
        listOf(orleans, paris, tours).forEach { fill(it) }
        assertTrue(fullyCached(tours))

        water.pruneUnreferenced()

        assertEquals(setOf(orleans.key, paris.key), cachedUnits())
        assertTrue(fullyCached(orleans))
        assertTrue(fullyCached(paris))
        val (commune, udi) = tours.communeCode to tours.udiCode
        assertNull(database.cache().sync(commune, udi))
        assertNull(database.cache().sample(commune, udi, "R-$udi"))
        assertNull(database.history().series(commune, udi, "1340"))
        assertNull(database.history().timelineSync(commune, udi))
        // The next display fetches it again.
        assertTrue(water.refreshCache(commune, udi, force = false))
    }

    @Test
    fun `a prune goes unit by unit, not commune by commune`() = runTest {
        preferences.addRecentUnit(orleans)
        fill(orleans)
        fill(orleansSud)

        water.pruneUnreferenced()

        assertEquals(setOf(orleans.key), cachedUnits())
        assertTrue(fullyCached(orleans))
    }

    @Test
    fun `a prune keeps the alert state of a followed unit`() = runTest {
        follow(paris)
        fill(paris)
        val seen = Instant.parse("2026-08-12T10:54:00Z")
        val sample = SampleSummary(
            code = "P1",
            date = seen,
            conclusion = null,
            bacteriologicalLimits = Compliance.COMPLIANT,
            physicochemicalLimits = Compliance.COMPLIANT,
            bacteriologicalReferences = Compliance.COMPLIANT,
            physicochemicalReferences = Compliance.COMPLIANT,
        )
        water.recordSeenSamples(
            paris.communeCode,
            paris.udiCode,
            seen,
            listOf(sample),
            Instant.EPOCH,
        )

        water.pruneUnreferenced()

        assertTrue(fullyCached(paris))
        assertEquals(
            SeenSamples(seen, setOf("P1"), Instant.EPOCH),
            water.seenSamples(paris.communeCode, paris.udiCode),
        )
    }

    @Test
    fun `the janitor prunes the orphans at start`() {
        runBlocking {
            follow(paris)
            preferences.addRecentUnit(orleans)
            listOf(orleans, paris, tours, orleansSud).forEach { fill(it) }
        }

        janitor.start(scope)

        eventually { cachedUnits() == setOf(orleans.key, paris.key) }
    }

    @Test
    fun `a recent removed by hand loses its cache`() {
        runBlocking {
            preferences.addRecentUnit(orleans)
            preferences.addRecentUnit(paris)
            fill(orleans)
            fill(paris)
        }
        janitor.start(scope)

        runBlocking { preferences.removeRecentUnit(orleans.communeCode, orleans.udiCode) }

        eventually { cachedUnits() == setOf(paris.key) }
    }

    @Test
    fun `a recent pushed out by a sixth unit loses its cache`() {
        val units = (1..AppPreferences.RECENT_LIMIT + 1).map {
            RecentUnit("C$it", "Commune $it", "U$it", "Unit $it")
        }
        runBlocking {
            units.take(AppPreferences.RECENT_LIMIT).forEach {
                preferences.addRecentUnit(it)
                fill(it)
            }
        }
        janitor.start(scope)
        eventually { cachedUnits().size == AppPreferences.RECENT_LIMIT }

        // The sixth is recorded first, then fetched, as the sheet does.
        runBlocking {
            preferences.addRecentUnit(units.last())
            fill(units.last())
        }

        eventually { cachedUnits() == units.drop(1).map { it.key }.toSet() }
        runBlocking { assertTrue(fullyCached(units.last())) }
    }

    @Test
    fun `an unfollowed unit loses its cache unless it is recent`() {
        runBlocking {
            follow(paris)
            follow(tours)
            preferences.addRecentUnit(tours)
            fill(paris)
            fill(tours)
        }
        janitor.start(scope)

        runBlocking {
            follows.unfollow(follows.find(tours.communeCode, tours.udiCode)!!.id)
            follows.unfollow(follows.find(paris.communeCode, paris.udiCode)!!.id)
        }

        eventually { cachedUnits() == setOf(tours.key) }
        runBlocking { assertTrue(fullyCached(tours)) }
    }

    @Test
    fun `a unit that leaves one list but stays in the other keeps its cache`() {
        runBlocking {
            follow(paris)
            preferences.addRecentUnit(paris)
            preferences.addRecentUnit(orleans)
            fill(paris)
            fill(orleans)
        }
        janitor.start(scope)

        runBlocking {
            // Paris leaves the recent list (still followed)...
            preferences.removeRecentUnit(paris.communeCode, paris.udiCode)
            // ...Orléans gets followed, then leaves the recent list.
            follow(orleans)
            preferences.removeRecentUnit(orleans.communeCode, orleans.udiCode)
            // Tours, cached without being referenced, shows that a prune
            // ran once another unit is opened.
            fill(tours)
            preferences.addRecentUnit(orleansSud)
        }

        eventually { tours.key !in cachedUnits() }
        runBlocking {
            assertTrue(fullyCached(paris))
            assertTrue(fullyCached(orleans))
        }
    }

    /** Runs [request] with a prune made while its Hub'Eau request is in flight. */
    private suspend fun <T> pruneDuring(request: suspend () -> T): T {
        network.during = {
            network.during = {}
            water.pruneUnreferenced()
        }
        return request().also { network.during = {} }
    }

    @Test
    fun `a fetch in flight for a pruned unit writes nothing`() = runTest {
        val (commune, udi) = tours.communeCode to tours.udiCode

        // Each answer is returned, but nothing is cached again.
        fill(tours)
        assertTrue(pruneDuring { water.refreshCache(commune, udi, force = true) })
        assertEquals(emptySet<UnitKey>(), cachedUnits())
        fill(tours)
        assertNotNull(
            pruneDuring { history.parameterSeries(commune, udi, setOf("1340"), YEAR_AGO, true) },
        )
        assertEquals(emptySet<UnitKey>(), cachedUnits())
        fill(tours)
        assertEquals(
            1,
            pruneDuring { history.sampleTimeline(commune, udi, YEAR_AGO, force = true) }.size,
        )
        assertEquals(emptySet<UnitKey>(), cachedUnits())
        fill(tours)
        assertNotNull(pruneDuring { samples.sample(commune, udi, "OTHER") })
        assertEquals(emptySet<UnitKey>(), cachedUnits())
    }

    @Test
    fun `a prune of another unit does not drop the fetch of a unit kept`() = runTest {
        preferences.addRecentUnit(orleans)
        val (commune, udi) = orleans.communeCode to orleans.udiCode

        // Tours is cached, unreferenced, and pruned during each request of Orleans.
        fill(tours)
        pruneDuring { water.refreshCache(commune, udi, force = true) }
        fill(tours)
        pruneDuring { samples.sample(commune, udi, "R-$udi") }
        fill(tours)
        pruneDuring { history.parameterSeries(commune, udi, setOf("1340"), YEAR_AGO, true) }
        fill(tours)
        pruneDuring { history.sampleTimeline(commune, udi, YEAR_AGO, force = true) }

        assertEquals(setOf(orleans.key), cachedUnits())
        assertTrue(fullyCached(orleans))
    }

    private companion object {
        val YEAR_AGO: LocalDate = LocalDate.parse("2025-10-24")
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 20L
    }
}

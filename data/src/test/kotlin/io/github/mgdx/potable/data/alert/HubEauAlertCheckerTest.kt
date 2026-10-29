package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.SeenSamples
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.MutableClock
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import io.github.mgdx.potable.data.local.sample
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HubEauAlertCheckerTest {
    /** The light request: answers [latest] per unit, or throws [failures]; counts the calls. */
    private val latest = mutableMapOf<String, SampleSummary?>()
    private val failures = mutableMapOf<String, Exception>()
    private val requested = mutableListOf<String>()

    /** The window request: answers the samples of [window] per unit in the days asked for. */
    private val window = mutableMapOf<String, List<SampleSummary>>()
    private val windowRequests = mutableListOf<Triple<String, LocalDate, LocalDate>>()

    /** Hub'Eau behind the cache refresh: the full sample, or [failure]. */
    private class FakeNetwork : WaterRepository {
        val samples = mutableMapOf<String, Sample?>()
        var failure: Exception? = null
        var calls = 0

        override suspend fun udis(communeCode: String): UdiChoice = UdiChoice.None

        override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? {
            calls++
            failure?.let { throw it }
            return samples[networkCode]
        }
    }

    private object Preferences : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() {}
    }

    private val database: PotableDatabase = inMemoryDatabase()
    private val follows = RoomFollowRepository(database)
    private val network = FakeNetwork()
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z"))
    private val water = CachingWaterRepository(network, database, Preferences, clock)

    private val checker =
        HubEauAlertChecker(
            follows,
            water,
            latest = { _, udiCode ->
                requested += udiCode
                failures[udiCode]?.let { throw it }
                latest[udiCode]
            },
            window = { _, udiCode, since, until ->
                windowRequests += Triple(udiCode, since, until)
                failures[udiCode]?.let { throw it }
                window[udiCode].orEmpty().filter {
                    val day = Samples.publishedDay(it.date)
                    !day.isBefore(since) && day.isBefore(until)
                }
            },
            clock = clock,
        )

    @After
    fun close() = database.close()

    private fun summary(
        code: String,
        date: String,
        conclusion: String = "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
            "pour l'ensemble des paramètres mesurés.",
        references: Compliance = Compliance.COMPLIANT,
    ) = SampleSummary(
        code = code,
        date = Instant.parse(date),
        conclusion = conclusion,
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT,
        bacteriologicalReferences = Compliance.COMPLIANT,
        physicochemicalReferences = references,
    )

    private suspend fun followOrleans() = follows.follow("45234", "Orléans", ORLEANS, "ORLEANS")

    private suspend fun followParis() = follows.follow("75056", "Paris", PARIS, "PARIS CENTRE")

    private suspend fun lastSeen(udiCode: String) =
        water.lastSeenSampleDate(if (udiCode == ORLEANS) "45234" else "75056", udiCode)

    @Test
    fun `nothing is asked when nothing is followed`() = runTest {
        assertEquals(emptyList<AlertEvent>(), checker.check())
        checker.setBaseline()

        assertEquals(emptyList<String>(), requested)
        assertEquals(emptyList<Triple<String, LocalDate, LocalDate>>(), windowRequests)
        assertEquals(0, network.calls)
    }

    @Test
    fun `the first check records a baseline and announces nothing`() = runTest {
        followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")

        assertEquals(emptyList<AlertEvent>(), checker.check())

        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(0, network.calls)
    }

    @Test
    fun `a unit without any sample records nothing`() = runTest {
        followOrleans()
        latest[ORLEANS] = null

        assertEquals(emptyList<AlertEvent>(), checker.check())

        assertNull(lastSeen(ORLEANS))
    }

    @Test
    fun `the same sample again is not new`() = runTest {
        followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        window[ORLEANS] = listOf(latest.getValue(ORLEANS)!!, summary("P0", "2026-08-01T10:00:00Z"))
        checker.check()

        assertEquals(emptyList<AlertEvent>(), checker.check())
        // An older sample already seen (the newest one withdrawn) is not new either.
        latest[ORLEANS] = summary("P0", "2026-08-01T10:00:00Z")
        window[ORLEANS] = listOf(summary("P0", "2026-08-01T10:00:00Z"))
        assertEquals(emptyList<AlertEvent>(), checker.check())

        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(0, network.calls)
    }

    @Test
    fun `a newer sample is announced, recorded and cached`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        val newer = summary("P2", "2026-09-15T09:00:00Z")
        latest[ORLEANS] = newer
        network.samples[ORLEANS] = sample("P2", "2026-09-15T09:00:00Z")

        val events = checker.check()

        assertEquals(listOf(AlertEvent(follow, newer, unfavourable = false)), events)
        assertEquals(newer.date, lastSeen(ORLEANS))
        assertEquals(1, network.calls)
        assertEquals("P2", water.cachedLatestSample("45234", ORLEANS).first()?.sample?.code)
        // Announced once only.
        assertEquals(emptyList<AlertEvent>(), checker.check())
    }

    @Test
    fun `the window is asked for from 92 days before the last one seen to today`() = runTest {
        followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        checker.check()

        // 2026-08-26 - 92 days = 2026-05-26, one day earlier; until 2026-10-24 + 2 days.
        val expected = Triple(ORLEANS, LocalDate.parse("2026-05-25"), LocalDate.parse("2026-10-26"))
        assertEquals(listOf(expected, expected), windowRequests)
        assertEquals(setOf("P1"), water.seenSamples("45234", ORLEANS).codes)
    }

    @Test
    fun `an unfavourable sample of the same day as a newer compliant one is announced`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P0", "2026-08-01T10:00:00Z")
        checker.check()
        // Vatry (51595), 2026-08-31: the newest row is the compliant sample.
        val compliant = summary("05100146767", "2026-08-31T14:05:00Z")
        val pesticides =
            summary("05100146860", "2026-08-31T14:04:00Z", references = Compliance.NON_COMPLIANT)
        latest[ORLEANS] = compliant
        window[ORLEANS] = listOf(compliant, pesticides)

        assertEquals(listOf(AlertEvent(follow, pesticides, unfavourable = true)), checker.check())
        assertEquals(compliant.date, lastSeen(ORLEANS))
        assertEquals(emptyList<AlertEvent>(), checker.check())
    }

    @Test
    fun `a second sample of the same day published after a check is announced`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P0", "2026-08-01T10:00:00Z")
        checker.check()
        val first = summary("05100146767", "2026-08-31T14:05:00Z")
        latest[ORLEANS] = first
        window[ORLEANS] = listOf(first)
        assertEquals("05100146767", checker.check().single().sample.code)

        val second =
            summary("05100146860", "2026-08-31T14:04:00Z", references = Compliance.NON_COMPLIANT)
        window[ORLEANS] = listOf(first, second)

        assertEquals(listOf(AlertEvent(follow, second, unfavourable = true)), checker.check())
    }

    @Test
    fun `a sample published late with an earlier date is announced`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P2", "2026-08-26T12:50:00Z")
        checker.check()
        val late = summary("P1", "2026-08-20T09:00:00Z")
        window[ORLEANS] = listOf(latest.getValue(ORLEANS)!!, late)

        assertEquals(listOf(AlertEvent(follow, late, unfavourable = false)), checker.check())
        // The newest date seen does not go back.
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(emptyList<AlertEvent>(), checker.check())
    }

    @Test
    fun `of several new samples, an older unfavourable one is the one announced`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        val unfavourable =
            summary("P2", "2026-09-01T08:00:00Z", references = Compliance.NON_COMPLIANT)
        val newest = summary("P3", "2026-09-20T08:00:00Z")
        latest[ORLEANS] = newest
        window[ORLEANS] = listOf(newest, unfavourable, summary("P1", "2026-08-26T12:50:00Z"))

        assertEquals(listOf(AlertEvent(follow, unfavourable, unfavourable = true)), checker.check())
        assertEquals(newest.date, lastSeen(ORLEANS))

        // All compliant: the newest is the one announced.
        val p4 = summary("P4", "2026-10-01T08:00:00Z")
        val p5 = summary("P5", "2026-10-02T08:00:00Z")
        latest[ORLEANS] = p5
        window[ORLEANS] = listOf(p5, p4, newest)
        assertEquals(listOf(AlertEvent(follow, p5, unfavourable = false)), checker.check())
    }

    @Test
    fun `a unit followed before the update compares dates once, then codes`() = runTest {
        val follow = followOrleans()
        // 0.3.1 kept the date only.
        water.setLastSeenSampleDate("45234", ORLEANS, Instant.parse("2026-08-26T12:50:00Z"))
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        // Older samples never recorded by code: not announced at the update.
        window[ORLEANS] = listOf(latest.getValue(ORLEANS)!!, summary("P0", "2026-08-20T09:00:00Z"))

        assertEquals(emptyList<AlertEvent>(), checker.check())
        assertEquals(setOf("P0", "P1"), water.seenSamples("45234", ORLEANS).codes)

        // From now on, a sample of the same day is found.
        val sameDay = summary("P9", "2026-08-26T12:49:00Z")
        window[ORLEANS] = window.getValue(ORLEANS) + sameDay
        assertEquals(listOf(AlertEvent(follow, sameDay, unfavourable = false)), checker.check())
    }

    @Test
    fun `the codes seen before the window are dropped`() = runTest {
        followOrleans()
        latest[ORLEANS] = summary("P1", "2026-05-01T09:00:00Z")
        checker.check()
        val newer = summary("P2", "2026-09-15T09:00:00Z")
        latest[ORLEANS] = newer
        window[ORLEANS] = listOf(newer)

        checker.check()

        assertEquals(
            SeenSamples(newer.date, setOf("P2"), newer.date.minus(Duration.ofDays(92))),
            water.seenSamples("45234", ORLEANS),
        )
    }

    @Test
    fun `a new sample the app already cached is announced without fetching it again`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        // The sheet was opened after the publication: the cache holds it.
        network.samples[ORLEANS] = sample("P2", "2026-09-15T09:00:00Z")
        water.refreshCache("45234", ORLEANS, force = false)
        val newer = summary("P2", "2026-09-15T09:00:00Z")
        latest[ORLEANS] = newer

        val events = checker.check()

        assertEquals(listOf(AlertEvent(follow, newer, unfavourable = false)), events)
        // The sheet's fetch only, and the light request of each check.
        assertEquals(1, network.calls)
        assertEquals(listOf(ORLEANS, ORLEANS), requested)
    }

    @Test
    fun `an unfavourable sample is marked as such`() = runTest {
        followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        // 06900172826, Lyon, 2025-10-22: references only, all codes C.
        latest[ORLEANS] = summary(
            "P2",
            "2026-09-15T09:00:00Z",
            conclusion = "Eau d'alimentation conforme aux limites de qualité et non conforme " +
                "aux références de qualité.",
        )
        assertTrue(checker.check().single().unfavourable)

        latest[ORLEANS] =
            summary("P3", "2026-10-01T09:00:00Z", references = Compliance.NON_COMPLIANT)
        assertTrue(checker.check().single().unfavourable)

        latest[ORLEANS] = summary("P4", "2026-10-20T09:00:00Z")
        assertFalse(checker.check().single().unfavourable)
    }

    @Test
    fun `a failed refresh does not hold the event back`() = runTest {
        val follow = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        val newer = summary("P2", "2026-09-15T09:00:00Z")
        latest[ORLEANS] = newer
        network.failure = DataError.Network(IOException("offline"))

        assertEquals(listOf(AlertEvent(follow, newer, unfavourable = false)), checker.check())

        assertEquals(1, network.calls)
        assertEquals(newer.date, lastSeen(ORLEANS))
    }

    @Test
    fun `a failed unit is skipped and checked again next time`() = runTest {
        followOrleans()
        val paris = followParis()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        latest[PARIS] = summary("Q1", "2026-08-20T08:00:00Z")
        checker.check()

        latest[ORLEANS] = summary("P2", "2026-09-15T09:00:00Z")
        failures[ORLEANS] = DataError.Timeout(IOException("slow"))
        val parisNewer = summary("Q2", "2026-09-16T08:00:00Z")
        latest[PARIS] = parisNewer

        assertEquals(listOf(AlertEvent(paris, parisNewer, unfavourable = false)), checker.check())
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(parisNewer.date, lastSeen(PARIS))

        // Any failure, not only Hub'Eau's, is skipped: the call never throws.
        failures[ORLEANS] = IllegalStateException("anything")
        assertEquals(emptyList<AlertEvent>(), checker.check())

        failures.remove(ORLEANS)
        assertEquals("P2", checker.check().single().sample.code)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        followOrleans()
        failures[ORLEANS] = CancellationException("stopped")

        try {
            checker.check()
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun `a baseline records the latest sample of every unit, without event`() = runTest {
        followOrleans()
        followParis()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        // Published while the alerts were off: not to be announced once they are on.
        latest[ORLEANS] = summary("P2", "2026-09-15T09:00:00Z")
        latest[PARIS] = summary("Q1", "2026-08-20T08:00:00Z")

        checker.setBaseline()

        assertEquals(Instant.parse("2026-09-15T09:00:00Z"), lastSeen(ORLEANS))
        assertEquals(Instant.parse("2026-08-20T08:00:00Z"), lastSeen(PARIS))
        assertEquals(0, network.calls)
        assertEquals(emptyList<AlertEvent>(), checker.check())
        latest[ORLEANS] = summary("P3", "2026-10-01T09:00:00Z")
        assertEquals("P3", checker.check().single().sample.code)
    }

    @Test
    fun `a baseline ignores failures`() = runTest {
        followOrleans()
        followParis()
        failures[ORLEANS] = DataError.Http(500)
        latest[PARIS] = summary("Q1", "2026-08-20T08:00:00Z")

        checker.setBaseline()

        assertNull(lastSeen(ORLEANS))
        assertNotNull(lastSeen(PARIS))
        assertEquals(listOf(ORLEANS, PARIS), requested)
    }

    @Test
    fun `a unit just followed gets its baseline from the cache, without request`() = runTest {
        // The sheet that was followed cached its latest sample.
        network.samples[ORLEANS] = sample("P1", "2026-08-26T12:50:00Z")
        water.refreshCache("45234", ORLEANS, force = false)
        val orleans = followOrleans()

        checker.setBaseline(orleans)

        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(emptyList<String>(), requested)
        assertEquals(emptyList<Triple<String, LocalDate, LocalDate>>(), windowRequests)
        // Published before the first periodic check: announced, not taken as the baseline.
        latest[ORLEANS] = summary("P2", "2026-09-15T09:00:00Z")
        assertEquals("P2", checker.check().single().sample.code)
    }

    @Test
    fun `a baseline from the cache knows the codes of its day only`() = runTest {
        network.samples[ORLEANS] = sample("P1", "2026-08-26T12:50:00Z")
        water.refreshCache("45234", ORLEANS, force = false)
        val orleans = followOrleans()
        checker.setBaseline(orleans)
        val p1 = summary("P1", "2026-08-26T12:50:00Z")
        latest[ORLEANS] = p1
        // Before the cached day: shown on no sheet, not judged, recorded.
        val older = summary("P0", "2026-08-20T09:00:00Z")
        // Same day, published after the sheet was cached: new.
        val sameDay = summary("P9", "2026-08-26T08:00:00Z")
        window[ORLEANS] = listOf(p1, sameDay, older)

        assertEquals(listOf(AlertEvent(orleans, sameDay, unfavourable = false)), checker.check())
        assertEquals(setOf("P0", "P1", "P9"), water.seenSamples("45234", ORLEANS).codes)
        assertEquals(emptyList<AlertEvent>(), checker.check())
    }

    @Test
    fun `a unit just followed without cache gets its baseline from Hub'Eau`() = runTest {
        val orleans = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")

        checker.setBaseline(orleans)

        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals(listOf(ORLEANS), requested)
        latest[ORLEANS] = summary("P2", "2026-09-15T09:00:00Z")
        assertEquals("P2", checker.check().single().sample.code)
    }

    @Test
    fun `the baseline of a unit just followed keeps a date already seen`() = runTest {
        val orleans = followOrleans()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        checker.check()
        // Not yet announced: following again must not swallow it.
        latest[ORLEANS] = summary("P2", "2026-09-15T09:00:00Z")

        checker.setBaseline(orleans)

        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
        assertEquals("P2", checker.check().single().sample.code)
    }

    @Test
    fun `the baseline of a unit just followed ignores a failure`() = runTest {
        val orleans = followOrleans()
        failures[ORLEANS] = DataError.Http(500)

        checker.setBaseline(orleans)

        assertNull(lastSeen(ORLEANS))
        // The first check falls back to recording it.
        failures.clear()
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")
        assertEquals(emptyList<AlertEvent>(), checker.check())
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), lastSeen(ORLEANS))
    }

    @Test
    fun `no baseline is kept for a unit unfollowed meanwhile`() = runTest {
        val orleans = followOrleans()
        follows.unfollow(orleans.id)
        latest[ORLEANS] = summary("P1", "2026-08-26T12:50:00Z")

        checker.setBaseline(orleans)

        assertNull(lastSeen(ORLEANS))
    }

    @Test
    fun `the application's checker needs the caching repository`() {
        val client = HubEauClient(OkHttpClient())
        assertTrue(AlertChecker(client, follows, water) is HubEauAlertChecker)
        try {
            AlertChecker(client, follows, network)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private companion object {
        const val ORLEANS = "045000474"
        const val PARIS = "075000221"
    }
}

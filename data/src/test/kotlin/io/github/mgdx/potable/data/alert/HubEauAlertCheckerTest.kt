package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HubEauClient
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HubEauAlertCheckerTest {
    /** The light request: answers [latest] per unit, or throws [failures]; counts the calls. */
    private val latest = mutableMapOf<String, SampleSummary?>()
    private val failures = mutableMapOf<String, Exception>()
    private val requested = mutableListOf<String>()

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
        HubEauAlertChecker(follows, water) { _, udiCode ->
            requested += udiCode
            failures[udiCode]?.let { throw it }
            latest[udiCode]
        }

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
        checker.check()

        assertEquals(emptyList<AlertEvent>(), checker.check())
        // An older sample (the newest one withdrawn) is not new either.
        latest[ORLEANS] = summary("P0", "2026-08-01T10:00:00Z")
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

package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Threshold
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.MutableClock
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.inMemoryDatabase
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/**
 * Regression found on a device (2026-10-25): Orléans pH showed its reference
 * "not drawn". Real Hub'Eau pages through the client, the series builder and
 * the Room cache, network then cache: the bounds survive every step.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryRealDataTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private val database: PotableDatabase = inMemoryDatabase()
    private val follows = RoomFollowRepository(database)
    private val clock = MutableClock(Instant.parse("2026-10-25T08:00:00Z"))

    private val repository by lazy {
        CachingHistoryRepository(
            RemoteHistoryRepository(
                HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/")),
                clock,
            ),
            database,
            object : AppPreferences {
                override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

                override suspend fun acceptDisclaimer() {}
            },
            clock,
        )
    }

    @After
    fun close() = database.close()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/hubeau/$name")) { "missing fixture $name" }.readText()

    private suspend fun series(code: String) =
        repository.parameterSeries("45234", UDI, setOf(code), LocalDate.parse("2026-07-01"))

    @Test
    fun `Orleans pH keeps its reference range, from the network and from the cache`() = runTest {
        follows.follow("45234", "Orléans", UDI, "ORLEANS")
        server.enqueue(response(fixture("resultats_dis_45234_ph.json")))

        val fetched = series("1302")!!
        val cached = series("1302")!!

        assertEquals(1, server.requestCount)
        for (ph in listOf(fetched, cached)) {
            assertEquals(10, ph.points.size)
            assertNull(ph.limit)
            assertEquals(Threshold(6.5, 9.0, "unité pH"), ph.reference)
        }
        assertEquals(fetched, cached)
    }

    @Test
    fun `Orleans nitrates keep their limit, from the network and from the cache`() = runTest {
        follows.follow("45234", "Orléans", UDI, "ORLEANS")
        server.enqueue(response(fixture("resultats_dis_45234_nitrates.json")))

        val fetched = series("1340")!!
        val cached = series("1340")!!

        assertEquals(1, server.requestCount)
        for (nitrates in listOf(fetched, cached)) {
            assertEquals(Threshold(null, 50.0, "mg/L"), nitrates.limit)
            assertNull(nitrates.reference)
        }
    }

    private companion object {
        const val UDI = "045000474"
    }
}

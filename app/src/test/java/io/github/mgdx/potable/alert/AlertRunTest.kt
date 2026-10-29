package io.github.mgdx.potable.alert

import io.github.mgdx.potable.data.alert.AlertEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AlertRunTest {
    private class FakePoster(var allowed: Boolean = true) : AlertPoster {
        val posted = mutableListOf<List<AlertEvent>>()

        override fun canPost(): Boolean = allowed

        override fun post(events: List<AlertEvent>) {
            posted += events
        }
    }

    private val compliant = event(1, unfavourable = false)
    private val unfavourable = event(2, unfavourable = true)
    private val checker = FakeChecker(listOf(compliant, unfavourable))
    private val poster = FakePoster()

    @Test
    fun `no request at all while both alerts are off`() = runTest {
        val outcome = AlertRun(AlertPreferences(), { checker }, poster).run()
        assertEquals(AlertRun.Outcome.ALERTS_OFF, outcome)
        assertEquals(0, checker.checks)
        assertEquals(emptyList<Any>(), poster.posted)
    }

    @Test
    fun `no request while notifications cannot be shown`() = runTest {
        poster.allowed = false
        val outcome = AlertRun(AlertPreferences(newAnalysis = true), { checker }, poster).run()
        assertEquals(AlertRun.Outcome.CANNOT_NOTIFY, outcome)
        assertEquals(0, checker.checks)
    }

    @Test
    fun `new analyses post every event`() = runTest {
        val outcome = AlertRun(AlertPreferences(newAnalysis = true), { checker }, poster).run()
        assertEquals(AlertRun.Outcome.CHECKED, outcome)
        assertEquals(listOf(listOf(compliant, unfavourable)), poster.posted)
    }

    @Test
    fun `unfavourable only posts the unfavourable event`() = runTest {
        AlertRun(AlertPreferences(unfavourable = true), { checker }, poster).run()
        assertEquals(1, checker.checks)
        assertEquals(listOf(listOf(unfavourable)), poster.posted)
    }

    private val now = Instant.parse("2026-10-29T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `a complete check is recorded as the last check`() = runTest {
        val preferences = AlertPreferences(newAnalysis = true)
        AlertRun(preferences, { checker }, poster, clock).run()
        assertEquals(now, preferences.lastAlertCheck.value)
        assertNull(preferences.lastAlertFailure.value)
    }

    @Test
    fun `an incomplete check is recorded as a failure, the last check stays`() = runTest {
        val preferences = AlertPreferences(newAnalysis = true)
        val before = Instant.parse("2026-10-22T08:00:00Z")
        preferences.lastAlertCheck.value = before
        checker.complete = false
        AlertRun(preferences, { checker }, poster, clock).run()
        assertEquals(before, preferences.lastAlertCheck.value)
        assertEquals(now, preferences.lastAlertFailure.value)
    }

    @Test
    fun `nothing is recorded when no check is made`() = runTest {
        val off = AlertPreferences()
        AlertRun(off, { checker }, poster, clock).run()
        poster.allowed = false
        val blocked = AlertPreferences(newAnalysis = true)
        AlertRun(blocked, { checker }, poster, clock).run()
        listOf(off, blocked).forEach {
            assertNull(it.lastAlertCheck.value)
            assertNull(it.lastAlertFailure.value)
        }
    }
}

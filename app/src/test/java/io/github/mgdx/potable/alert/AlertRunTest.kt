package io.github.mgdx.potable.alert

import io.github.mgdx.potable.data.alert.AlertEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
}

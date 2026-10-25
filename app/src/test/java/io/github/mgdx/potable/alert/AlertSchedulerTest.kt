package io.github.mgdx.potable.alert

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlertSchedulerTest {
    /** Records every call, in order: "schedule 2", "cancel". */
    private class FakeWork : AlertWork {
        val calls = mutableListOf<String>()

        override fun schedule(periodWeeks: Int) {
            calls += "schedule $periodWeeks"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private val work = FakeWork()
    private val checker = FakeChecker()

    private fun TestScope.started(preferences: AlertPreferences) {
        backgroundScope.launch { AlertScheduler(preferences, { checker }, work).observe() }
        runCurrent()
    }

    @Test
    fun `a fresh install plans nothing and records no baseline`() = runTest {
        started(AlertPreferences())
        assertEquals(listOf("cancel"), work.calls)
        assertEquals(0, checker.baselines)
    }

    @Test
    fun `switching an alert on records a baseline, then plans the period`() = runTest {
        val preferences = AlertPreferences()
        started(preferences)
        preferences.alertNewAnalysis.value = true
        runCurrent()
        assertEquals(1, checker.baselines)
        assertEquals(listOf("cancel", "schedule 1"), work.calls)
    }

    @Test
    fun `a second alert or a new period plans again without a new baseline`() = runTest {
        val preferences = AlertPreferences()
        started(preferences)
        preferences.alertUnfavourable.value = true
        runCurrent()
        preferences.alertNewAnalysis.value = true
        runCurrent()
        preferences.alertPeriodWeeks.value = 4
        runCurrent()
        assertEquals(1, checker.baselines)
        assertEquals(listOf("cancel", "schedule 1", "schedule 1", "schedule 4"), work.calls)
    }

    @Test
    fun `switching every alert off cancels the task, switching back records a baseline again`() =
        runTest {
            val preferences = AlertPreferences(newAnalysis = true)
            started(preferences)
            preferences.alertNewAnalysis.value = false
            runCurrent()
            preferences.alertUnfavourable.value = true
            runCurrent()
            assertEquals(listOf("schedule 1", "cancel", "schedule 1"), work.calls)
            assertEquals(1, checker.baselines)
        }

    @Test
    fun `alerts already on at start are planned without a baseline`() = runTest {
        val preferences = AlertPreferences(unfavourable = true)
        preferences.alertPeriodWeeks.value = 2
        started(preferences)
        assertEquals(listOf("schedule 2"), work.calls)
        assertEquals(0, checker.baselines)
    }

    @Test
    fun `a failed baseline still plans the task`() = runTest {
        checker.baselineError = IOException("offline")
        val preferences = AlertPreferences()
        started(preferences)
        preferences.alertNewAnalysis.value = true
        runCurrent()
        assertEquals(listOf("cancel", "schedule 1"), work.calls)
    }
}

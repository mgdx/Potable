package io.github.mgdx.potable.ui.settings

import io.github.mgdx.potable.alert.AlertCheckNow
import io.github.mgdx.potable.alert.AlertRun
import io.github.mgdx.potable.alert.CheckNowState
import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.FakeCommuneRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** "Check now" in Settings → Alerts (SPEC §5 F7). */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckNowViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Preferences : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = MutableStateFlow(true)

        override suspend fun acceptDisclaimer() = Unit

        override val alertNewAnalysis = MutableStateFlow(true)
        override val alertUnfavourable = MutableStateFlow(false)
    }

    private class Access(var allowed: Boolean = true) : NotificationAccess {
        override fun canPost(): Boolean = allowed

        override val canRequest: Boolean = true
    }

    /** What WorkManager would say; [starts] counts the requests. */
    private class FakeCheckNow(initial: CheckNowState = CheckNowState.Idle) : AlertCheckNow {
        override val state = MutableStateFlow(initial)
        var starts = 0

        override fun start() {
            starts++
        }
    }

    private val preferences = Preferences()
    private val access = Access()
    private val work = FakeCheckNow()
    private val follows = FakeFollowRepository(listOf(follow(1)))

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(
            preferences,
            FakeWaterRepository(),
            access,
            FakeCommuneRepository(emptyList()),
            follows,
            checkNow = work,
        )
        backgroundScope.launch(main.dispatcher) { viewModel.checkNowUi.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `shown only while an alert is on`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.checkNowUi.value.visible)
        assertTrue(viewModel.checkNowUi.value.enabled)
        preferences.alertNewAnalysis.value = false
        advanceUntilIdle()
        assertFalse(viewModel.checkNowUi.value.visible)
        preferences.alertUnfavourable.value = true
        advanceUntilIdle()
        assertTrue(viewModel.checkNowUi.value.visible)
    }

    @Test
    fun `disabled while no commune is followed`() = runTest(main.dispatcher) {
        follows.unfollow(1)
        val viewModel = viewModel()
        assertTrue(viewModel.checkNowUi.value.visible)
        assertFalse(viewModel.checkNowUi.value.enabled)
        viewModel.checkAlertsNow()
        assertEquals(0, work.starts)
    }

    @Test
    fun `disabled while notifications are not allowed`() = runTest(main.dispatcher) {
        access.allowed = false
        val viewModel = viewModel()
        assertFalse(viewModel.checkNowUi.value.enabled)
        viewModel.checkAlertsNow()
        assertEquals(0, work.starts)
    }

    @Test
    fun `a check waits, runs, then says that nothing is new, once`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.checkAlertsNow()
        assertEquals(1, work.starts)

        work.state.value = CheckNowState.Waiting
        advanceUntilIdle()
        assertEquals(CheckNowProgress.WAITING, viewModel.checkNowUi.value.progress)
        assertFalse(viewModel.checkNowUi.value.enabled)
        // A second tap while it waits starts nothing.
        viewModel.checkAlertsNow()
        assertEquals(1, work.starts)

        work.state.value = CheckNowState.Running
        advanceUntilIdle()
        assertEquals(CheckNowProgress.RUNNING, viewModel.checkNowUi.value.progress)

        work.state.value = CheckNowState.Finished(UUID.randomUUID(), AlertRun.Outcome.NOTHING_NEW)
        advanceUntilIdle()
        assertEquals(CheckNowProgress.IDLE, viewModel.checkNowUi.value.progress)
        assertTrue(viewModel.checkNowUi.value.enabled)
        assertEquals(CheckNowMessage.NOTHING_NEW, viewModel.checkNowMessage.value)
        viewModel.onCheckNowMessageShown()
        assertNull(viewModel.checkNowMessage.value)
    }

    @Test
    fun `a failed check says so, notifications sent say nothing more`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.checkAlertsNow()
        work.state.value = CheckNowState.Running
        advanceUntilIdle()
        work.state.value = CheckNowState.Finished(UUID.randomUUID(), AlertRun.Outcome.FAILED)
        advanceUntilIdle()
        assertEquals(CheckNowMessage.FAILED, viewModel.checkNowMessage.value)
        viewModel.onCheckNowMessageShown()

        viewModel.checkAlertsNow()
        work.state.value = CheckNowState.Running
        advanceUntilIdle()
        work.state.value = CheckNowState.Finished(UUID.randomUUID(), AlertRun.Outcome.NOTIFIED)
        advanceUntilIdle()
        assertNull(viewModel.checkNowMessage.value)
    }

    @Test
    fun `the end of an earlier check is not told again`() = runTest(main.dispatcher) {
        val earlier = CheckNowState.Finished(UUID.randomUUID(), AlertRun.Outcome.NOTHING_NEW)
        work.state.value = earlier
        val viewModel = viewModel()
        assertNull(viewModel.checkNowMessage.value)
        // Tapped: WorkManager still says the earlier one before the new one.
        viewModel.checkAlertsNow()
        work.state.value = CheckNowState.Finished(earlier.id, earlier.outcome)
        advanceUntilIdle()
        assertNull(viewModel.checkNowMessage.value)
    }
}

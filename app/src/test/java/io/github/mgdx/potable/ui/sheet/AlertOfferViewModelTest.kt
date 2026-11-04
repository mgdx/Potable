package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Following a unit offers, once per install, to switch the alerts on (SPEC §5 F7). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertOfferViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Preferences : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = MutableStateFlow(true)

        override suspend fun acceptDisclaimer() = Unit

        override val alertNewAnalysis = MutableStateFlow(false)
        override val alertUnfavourable = MutableStateFlow(false)
        override val alertsOfferShown = MutableStateFlow(false)

        override suspend fun setAlertNewAnalysis(enabled: Boolean) {
            alertNewAnalysis.value = enabled
        }

        override suspend fun setAlertsOfferShown() {
            alertsOfferShown.value = true
        }
    }

    private class Access(var allowed: Boolean, override val canRequest: Boolean = true) :
        NotificationAccess {
        override fun canPost(): Boolean = allowed
    }

    private val preferences = Preferences()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(
        follows: FakeFollowRepository = FakeFollowRepository(),
        access: Access = Access(allowed = true),
    ) = SheetViewModel(
        FakeWaterRepository(),
        follows,
        FakeHistoryRepository(),
        "45234",
        "045000123",
        "Orléans",
        "ORLEANS",
        clock,
        recents = preferences,
        notifications = access,
    )

    @Test
    fun `following offers the alerts once, and the offer is marked as made`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.toggleFollow()
            advanceUntilIdle()
            assertEquals(AlertOffer.OFFER, viewModel.alertOffer.value)
            assertTrue(preferences.alertsOfferShown.value)
            // Dismissed: not shown again, nothing switched on.
            viewModel.onAlertOfferShown(AlertOffer.OFFER)
            assertNull(viewModel.alertOffer.value)
            assertFalse(preferences.alertNewAnalysis.value)
        }

    @Test
    fun `never a second time, even on another sheet`() = runTest(main.dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.toggleFollow()
        advanceUntilIdle()
        first.onAlertOfferShown(AlertOffer.OFFER)
        // Unfollowed then followed again.
        first.toggleFollow()
        advanceUntilIdle()
        first.toggleFollow()
        advanceUntilIdle()
        assertNull(first.alertOffer.value)

        val second = viewModel()
        advanceUntilIdle()
        second.toggleFollow()
        advanceUntilIdle()
        assertNull(second.alertOffer.value)
    }

    @Test
    fun `unfollowing offers nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel(FakeFollowRepository(listOf(follow(1))))
        advanceUntilIdle()
        viewModel.toggleFollow()
        advanceUntilIdle()
        assertNull(viewModel.alertOffer.value)
        assertFalse(preferences.alertsOfferShown.value)
    }

    @Test
    fun `nothing is offered while an alert is already on`() = runTest(main.dispatcher) {
        preferences.alertUnfavourable.value = true
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.toggleFollow()
        advanceUntilIdle()
        assertNull(viewModel.alertOffer.value)
        assertFalse(preferences.alertsOfferShown.value)
    }

    @Test
    fun `turning on with notifications allowed stores the new analysis alert`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.toggleFollow()
            advanceUntilIdle()
            viewModel.onAlertOfferShown(AlertOffer.OFFER)
            viewModel.enableAlerts()
            advanceUntilIdle()
            assertTrue(preferences.alertNewAnalysis.value)
            assertFalse(preferences.alertUnfavourable.value)
            assertFalse(viewModel.permissionRequest.value)
            assertNull(viewModel.alertOffer.value)
        }

    @Test
    fun `the permission is asked first, and the alert stored once granted`() =
        runTest(main.dispatcher) {
            val access = Access(allowed = false)
            val viewModel = viewModel(access = access)
            advanceUntilIdle()
            viewModel.toggleFollow()
            advanceUntilIdle()
            viewModel.enableAlerts()
            assertTrue(viewModel.permissionRequest.value)
            viewModel.onPermissionRequested()
            assertFalse(viewModel.permissionRequest.value)
            access.allowed = true
            viewModel.onPermissionResult(true)
            advanceUntilIdle()
            assertTrue(preferences.alertNewAnalysis.value)
            assertNull(viewModel.alertOffer.value)
        }

    @Test
    fun `a refused permission switches nothing on, and says so`() = runTest(main.dispatcher) {
        val viewModel = viewModel(access = Access(allowed = false))
        advanceUntilIdle()
        viewModel.toggleFollow()
        advanceUntilIdle()
        viewModel.enableAlerts()
        viewModel.onPermissionRequested()
        viewModel.onPermissionResult(false)
        advanceUntilIdle()
        assertFalse(preferences.alertNewAnalysis.value)
        assertEquals(AlertOffer.DENIED, viewModel.alertOffer.value)
    }

    @Test
    fun `below Android 13, blocked notifications switch nothing on, and say so`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel(access = Access(allowed = false, canRequest = false))
            advanceUntilIdle()
            viewModel.toggleFollow()
            advanceUntilIdle()
            // Offered all the same: the action leads to the system settings.
            assertEquals(AlertOffer.OFFER, viewModel.alertOffer.value)
            viewModel.enableAlerts()
            advanceUntilIdle()
            assertFalse(viewModel.permissionRequest.value)
            assertFalse(preferences.alertNewAnalysis.value)
            assertEquals(AlertOffer.DENIED, viewModel.alertOffer.value)
        }
}

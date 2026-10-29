package io.github.mgdx.potable.ui.settings

import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.CachedCommune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.ThemeMode
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakePreferences : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() = Unit

        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val historyYears = MutableStateFlow(AppPreferences.DEFAULT_YEARS)
        override val retentionYears = MutableStateFlow(AppPreferences.DEFAULT_YEARS)

        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }

        override suspend fun setHistoryYears(years: Int) {
            historyYears.value = years
        }

        override suspend fun setRetentionYears(years: Int) {
            retentionYears.value = years
        }

        override val alertNewAnalysis = MutableStateFlow(false)
        override val alertUnfavourable = MutableStateFlow(false)
        override val alertPeriodWeeks = MutableStateFlow(AppPreferences.DEFAULT_ALERT_WEEKS)

        override suspend fun setAlertNewAnalysis(enabled: Boolean) {
            alertNewAnalysis.value = enabled
        }

        override suspend fun setAlertUnfavourable(enabled: Boolean) {
            alertUnfavourable.value = enabled
        }

        override suspend fun setAlertPeriodWeeks(weeks: Int) {
            alertPeriodWeeks.value = weeks
        }
    }

    /** Notifications allowed or not; [canRequest] is false below Android 13. */
    private class FakeAccess(
        var allowed: Boolean = false,
        override val canRequest: Boolean = true,
    ) : NotificationAccess {
        override fun canPost(): Boolean = allowed
    }

    /**
     * Counts purges; [gate] holds a purge in progress until completed.
     * [retentionPurges] records the retention read by each retention purge.
     */
    private class FakeWater(
        val gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
        val retention: () -> Int = { 0 },
    ) : WaterRepository {
        var purges = 0
        val retentionPurges = mutableListOf<Int>()

        override suspend fun purgeOlderThanRetention() {
            retentionPurges += retention()
        }

        override suspend fun udis(communeCode: String): UdiChoice = error("not used")

        override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? =
            error("not used")

        override suspend fun purgeCache() {
            purges++
            gate.await()
        }

        val cached = MutableStateFlow<List<CachedCommune>>(emptyList())
        val communePurges = mutableListOf<String>()

        override fun cachedCommunes(): Flow<List<CachedCommune>> = cached

        override suspend fun purgeCommune(communeCode: String) {
            communePurges += communeCode
            cached.value = cached.value.filter { it.communeCode != communeCode }
        }
    }

    /** The embedded referential: two communes. */
    private val communes = object : CommuneRepository {
        override suspend fun search(query: String, limit: Int): List<Commune> = emptyList()

        override suspend fun nearest(
            latitude: Double,
            longitude: Double,
            maxDistanceKm: Double?,
        ): Commune? = null

        override suspend fun byCode(code: String): Commune? = mapOf(
            "45234" to Commune("45234", "Orléans", listOf("45000"), 47.9, 1.9),
            "75056" to Commune("75056", "Paris", listOf("75001"), 48.9, 2.3),
        )[code]
    }

    private val preferences = FakePreferences()
    private val access = FakeAccess()

    /** Collects the screen's states, as the screen does, for the test's duration. */
    private fun TestScope.subscribed(viewModel: SettingsViewModel): SettingsViewModel {
        backgroundScope.launch(main.dispatcher) { viewModel.settings.collect {} }
        backgroundScope.launch(main.dispatcher) { viewModel.notice.collect {} }
        return viewModel
    }

    @Test
    fun `the stored settings are shown, nothing before they are read`() = runTest(main.dispatcher) {
        preferences.themeMode.value = ThemeMode.DARK
        preferences.historyYears.value = 5
        preferences.retentionYears.value = 10
        val viewModel = SettingsViewModel(preferences, FakeWater(), access, communes)
        assertNull(viewModel.settings.value)
        subscribed(viewModel)
        advanceUntilIdle()
        assertEquals(Settings(ThemeMode.DARK, 5, 10, ALERTS_OFF), viewModel.settings.value)
    }

    @Test
    fun `choosing a theme stores it and shows it`() = runTest(main.dispatcher) {
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)) {
            viewModel.setThemeMode(mode)
            advanceUntilIdle()
            assertEquals(mode, preferences.themeMode.value)
            assertEquals(mode, viewModel.settings.value?.themeMode)
        }
    }

    @Test
    fun `choosing the history and retention windows stores them`() = runTest(main.dispatcher) {
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        viewModel.setHistoryYears(1)
        viewModel.setRetentionYears(10)
        advanceUntilIdle()
        assertEquals(1, preferences.historyYears.value)
        assertEquals(10, preferences.retentionYears.value)
        assertEquals(Settings(ThemeMode.SYSTEM, 1, 10, ALERTS_OFF), viewModel.settings.value)
    }

    @Test
    fun `a window outside the offered choices is ignored`() = runTest(main.dispatcher) {
        val viewModel = SettingsViewModel(preferences, FakeWater(), access, communes)
        viewModel.setHistoryYears(2)
        viewModel.setRetentionYears(0)
        advanceUntilIdle()
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.historyYears.value)
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.retentionYears.value)
    }

    @Test
    fun `a new retention purges the cache at once, once stored`() = runTest(main.dispatcher) {
        val water = FakeWater(retention = { preferences.retentionYears.value })
        val viewModel = SettingsViewModel(preferences, water, access, communes)
        viewModel.setRetentionYears(1)
        advanceUntilIdle()
        // The purge reads the new value: it runs after the preference is written.
        assertEquals(listOf(1), water.retentionPurges)
        viewModel.setRetentionYears(0)
        advanceUntilIdle()
        assertEquals(listOf(1), water.retentionPurges)
    }

    @Test
    fun `the cache is not cleared without confirmation`() = runTest(main.dispatcher) {
        val water = FakeWater()
        val viewModel = SettingsViewModel(preferences, water, access, communes)
        viewModel.confirmPurge()
        viewModel.requestPurge()
        assertEquals(PurgeState.CONFIRMING, viewModel.purge.value)
        viewModel.dismissPurge()
        viewModel.confirmPurge()
        advanceUntilIdle()
        assertEquals(0, water.purges)
        assertEquals(PurgeState.IDLE, viewModel.purge.value)
    }

    @Test
    fun `the cache is cleared once after confirmation, then a message is due once`() =
        runTest(main.dispatcher) {
            val water = FakeWater(gate = CompletableDeferred())
            val viewModel = SettingsViewModel(preferences, water, access, communes)
            viewModel.requestPurge()
            viewModel.confirmPurge()
            // A second tap on the button, or on the dialog, does not purge again.
            viewModel.confirmPurge()
            viewModel.requestPurge()
            advanceUntilIdle()
            assertEquals(PurgeState.RUNNING, viewModel.purge.value)
            water.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, water.purges)
            assertEquals(PurgeState.DONE, viewModel.purge.value)
            viewModel.onPurgeMessageShown()
            assertEquals(PurgeState.IDLE, viewModel.purge.value)
        }

    @Test
    fun `alerts are off by default and nothing is asked`() = runTest(main.dispatcher) {
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        advanceUntilIdle()
        assertEquals(ALERTS_OFF, viewModel.settings.value?.alerts)
        assertFalse(viewModel.permissionRequest.value)
        assertEquals(NotificationNotice.NONE, viewModel.notice.value)
    }

    @Test
    fun `switching an alert on with notifications allowed stores it at once`() =
        runTest(main.dispatcher) {
            access.allowed = true
            val viewModel =
                subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
            viewModel.setAlert(AlertKind.UNFAVOURABLE, true)
            advanceUntilIdle()
            assertTrue(preferences.alertUnfavourable.value)
            assertFalse(viewModel.permissionRequest.value)
            viewModel.setAlert(AlertKind.UNFAVOURABLE, false)
            advanceUntilIdle()
            assertFalse(preferences.alertUnfavourable.value)
        }

    @Test
    fun `switching an alert on asks for the permission, and stores it once granted`() =
        runTest(main.dispatcher) {
            val viewModel =
                subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
            viewModel.setAlert(AlertKind.NEW_ANALYSIS, true)
            advanceUntilIdle()
            assertTrue(viewModel.permissionRequest.value)
            assertFalse(preferences.alertNewAnalysis.value)
            viewModel.onPermissionRequested()
            assertFalse(viewModel.permissionRequest.value)
            access.allowed = true
            viewModel.onPermissionResult(granted = true)
            advanceUntilIdle()
            assertTrue(preferences.alertNewAnalysis.value)
            assertEquals(NotificationNotice.NONE, viewModel.notice.value)
        }

    @Test
    fun `a refused permission leaves the alert off and explains why`() = runTest(main.dispatcher) {
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        viewModel.setAlert(AlertKind.NEW_ANALYSIS, true)
        viewModel.onPermissionRequested()
        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()
        assertFalse(preferences.alertNewAnalysis.value)
        assertEquals(NotificationNotice.DENIED, viewModel.notice.value)
        // Allowed later in the system settings: the explanation goes away.
        access.allowed = true
        viewModel.refreshNotificationAccess()
        advanceUntilIdle()
        assertEquals(NotificationNotice.NONE, viewModel.notice.value)
    }

    @Test
    fun `without a runtime permission, notifications turned off are explained, not asked`() =
        runTest(main.dispatcher) {
            val old = FakeAccess(allowed = false, canRequest = false)
            val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), old, communes))
            viewModel.setAlert(AlertKind.UNFAVOURABLE, true)
            advanceUntilIdle()
            assertFalse(viewModel.permissionRequest.value)
            assertFalse(preferences.alertUnfavourable.value)
            assertEquals(NotificationNotice.DENIED, viewModel.notice.value)
        }

    @Test
    fun `a permission revoked while an alert is on is reported`() = runTest(main.dispatcher) {
        access.allowed = true
        preferences.alertNewAnalysis.value = true
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        advanceUntilIdle()
        assertEquals(NotificationNotice.NONE, viewModel.notice.value)
        access.allowed = false
        viewModel.refreshNotificationAccess()
        advanceUntilIdle()
        assertEquals(NotificationNotice.BLOCKED, viewModel.notice.value)
    }

    @Test
    fun `the alert period is stored, only among the offered choices`() = runTest(main.dispatcher) {
        val viewModel = subscribed(SettingsViewModel(preferences, FakeWater(), access, communes))
        viewModel.setAlertPeriodWeeks(4)
        advanceUntilIdle()
        assertEquals(4, preferences.alertPeriodWeeks.value)
        assertEquals(4, viewModel.settings.value?.alerts?.periodWeeks)
        viewModel.setAlertPeriodWeeks(3)
        advanceUntilIdle()
        assertEquals(4, preferences.alertPeriodWeeks.value)
    }

    @Test
    fun `the cached communes are listed by name, the code standing for an unknown one`() =
        runTest(main.dispatcher) {
            val water = FakeWater()
            val fetched = Instant.parse("2026-10-24T08:00:00Z")
            water.cached.value = listOf(
                CachedCommune("45234", setOf("045000474"), fetched, sizeBytes = 42_000),
                CachedCommune("75056", setOf("075000221", "075000222"), null),
                CachedCommune("00000", setOf("000000001"), fetched, sizeBytes = 7),
            )
            val viewModel = SettingsViewModel(preferences, water, access, communes)
            assertNull(viewModel.cachedCommunes.value)
            backgroundScope.launch(main.dispatcher) { viewModel.cachedCommunes.collect {} }
            advanceUntilIdle()
            assertEquals(
                listOf(
                    CachedCommuneItem("00000", null, fetched, sizeBytes = 7),
                    CachedCommuneItem("45234", "Orléans", fetched, sizeBytes = 42_000),
                    CachedCommuneItem("75056", "Paris", null),
                ),
                viewModel.cachedCommunes.value,
            )
            assertEquals("00000", viewModel.cachedCommunes.value!!.first().label)
        }

    @Test
    fun `a commune's cache is cleared once, after confirmation`() = runTest(main.dispatcher) {
        val water = FakeWater()
        water.cached.value = listOf(CachedCommune("45234", setOf("045000474"), null))
        val viewModel = SettingsViewModel(preferences, water, access, communes)
        backgroundScope.launch(main.dispatcher) { viewModel.cachedCommunes.collect {} }
        advanceUntilIdle()
        val orleans = viewModel.cachedCommunes.value!!.single()

        viewModel.requestCommunePurge(orleans)
        viewModel.dismissCommunePurge()
        advanceUntilIdle()
        assertNull(viewModel.communeToPurge.value)
        assertTrue(water.communePurges.isEmpty())

        viewModel.requestCommunePurge(orleans)
        assertEquals(orleans, viewModel.communeToPurge.value)
        viewModel.confirmCommunePurge()
        viewModel.confirmCommunePurge()
        advanceUntilIdle()
        assertEquals(listOf("45234"), water.communePurges)
        assertNull(viewModel.communeToPurge.value)
        assertEquals(orleans, viewModel.communePurged.value)
        assertEquals(emptyList<CachedCommuneItem>(), viewModel.cachedCommunes.value)
        // Purging one commune is not the whole purge.
        assertEquals(0, water.purges)

        viewModel.onCommunePurgeMessageShown()
        assertNull(viewModel.communePurged.value)
    }

    @Test
    fun `the alerts say so while no commune is followed`() = runTest(main.dispatcher) {
        val follows = FakeFollowRepository()
        val viewModel = SettingsViewModel(preferences, FakeWater(), access, communes, follows)
        backgroundScope.launch(main.dispatcher) { viewModel.noFollows.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.noFollows.value)

        follows.follow("45234", "Orléans", "045000474", "ORLEANS")
        advanceUntilIdle()
        assertFalse(viewModel.noFollows.value)

        follows.unfollow(follows.current.single().id)
        advanceUntilIdle()
        assertTrue(viewModel.noFollows.value)
    }

    @Test
    fun `nothing is said when the follows cannot be read`() = runTest(main.dispatcher) {
        val unreadable = object : FollowRepository by FakeFollowRepository() {
            override val follows: Flow<List<Follow>> = flow { throw IOException("disk") }
        }
        val viewModel = SettingsViewModel(preferences, FakeWater(), access, communes, unreadable)
        backgroundScope.launch(main.dispatcher) { viewModel.noFollows.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.noFollows.value)
    }

    private companion object {
        val ALERTS_OFF = AlertSettings(
            newAnalysis = false,
            unfavourable = false,
            periodWeeks = AppPreferences.DEFAULT_ALERT_WEEKS,
        )
    }
}

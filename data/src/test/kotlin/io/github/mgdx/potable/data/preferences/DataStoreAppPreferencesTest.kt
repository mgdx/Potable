package io.github.mgdx.potable.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreAppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = runBlocking { closeAll() }

    /**
     * Closes every open DataStore, as the process dying would. `cancel()` alone
     * is not enough: it returns while the store's coroutines may still be
     * running, and the store only releases its file once its scope's job has
     * completed. Reopening the file before that throws "There are multiple
     * DataStores active for the same file". Joining waits for the release.
     */
    private suspend fun closeAll() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    /** A fresh DataStore over [file], as a new process would open it. */
    private fun store(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun preferences(file: File) = DataStoreAppPreferences(store(file))

    private fun storeFile() = File(folder.root, "preferences.preferences_pb")

    @Test
    fun `the notice is not accepted on a fresh install`() = runBlocking {
        assertFalse(preferences(storeFile()).disclaimerAccepted.first())
    }

    @Test
    fun `accepting the notice is remembered`() = runBlocking {
        val preferences = preferences(storeFile())
        preferences.acceptDisclaimer()
        assertTrue(preferences.disclaimerAccepted.first())
    }

    @Test
    fun `acceptance survives a restart`() = runBlocking {
        val file = storeFile()
        preferences(file).acceptDisclaimer()
        closeAll()
        assertTrue(preferences(file).disclaimerAccepted.first())
    }

    @Test
    fun `settings have their defaults on a fresh install`() = runBlocking {
        val preferences = preferences(storeFile())
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.historyYears.first())
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.retentionYears.first())
        assertNull(preferences.rememberedUdi("75056"))
    }

    @Test
    fun `settings are remembered across a restart`() = runBlocking {
        val file = storeFile()
        preferences(file).apply {
            setThemeMode(ThemeMode.DARK)
            setHistoryYears(5)
            setRetentionYears(10)
        }
        closeAll()

        val preferences = preferences(file)
        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertEquals(5, preferences.historyYears.first())
        assertEquals(10, preferences.retentionYears.first())
    }

    @Test
    fun `a year count outside the choices falls back to the default`() = runBlocking {
        val store = store(storeFile())
        val preferences = DataStoreAppPreferences(store)
        preferences.setHistoryYears(7)
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.historyYears.first())

        // A value written by another version of the application.
        store.edit {
            it[intPreferencesKey("retention_years")] = 0
            it[stringPreferencesKey("theme_mode")] = "SEPIA"
        }
        assertEquals(AppPreferences.DEFAULT_YEARS, preferences.retentionYears.first())
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }

    @Test
    fun `the unit chosen for a commune is remembered per commune`() = runBlocking {
        val file = storeFile()
        preferences(file).apply {
            rememberUdi("75056", "075000001")
            rememberUdi("75056", "075000002")
            rememberUdi("13055", "013000001")
        }
        closeAll()

        val preferences = preferences(file)
        assertEquals("075000002", preferences.rememberedUdi("75056"))
        assertEquals("013000001", preferences.rememberedUdi("13055"))
        assertNull(preferences.rememberedUdi("45234"))
    }

    @Test
    fun `the name of the unit chosen is kept with it`() = runBlocking {
        val file = storeFile()
        preferences(file).apply {
            rememberUdi("75056", "075000221", "PARIS CENTRE")
            rememberUdi("13055", "013000001", "MARSEILLE NORD")
            // Written without a name: no name of another unit is left behind.
            rememberUdi("13055", "013000002")
        }
        closeAll()

        val preferences = preferences(file)
        assertEquals("075000221", preferences.rememberedUdi("75056"))
        assertEquals(
            mapOf(
                "75056" to RememberedUdi("075000221", "PARIS CENTRE"),
                "13055" to RememberedUdi("013000002", null),
            ),
            preferences.rememberedUdis.first(),
        )
    }

    @Test
    fun `a unit remembered before names were kept reads without a name`() = runBlocking {
        val file = storeFile()
        val store = store(file)
        store.edit { it[stringPreferencesKey("udi_75056")] = "075000221" }
        val preferences = DataStoreAppPreferences(store)
        assertEquals(
            mapOf("75056" to RememberedUdi("075000221", null)),
            preferences.rememberedUdis.first(),
        )
        assertEquals("075000221", preferences.rememberedUdi("75056"))
    }

    @Test
    fun `forgetting the remembered units forgets their names, but the kept ones`() = runBlocking {
        val preferences = preferences(storeFile())
        preferences.rememberUdi("75056", "075000221", "PARIS CENTRE")
        preferences.rememberUdi("13055", "013000001", "MARSEILLE NORD")

        preferences.forgetRememberedUdis(keep = setOf("13055"))

        assertEquals(
            mapOf("13055" to RememberedUdi("013000001", "MARSEILLE NORD")),
            preferences.rememberedUdis.first(),
        )
        // Remembered again without a name: none from before comes back.
        preferences.rememberUdi("75056", "075000221")
        assertNull(preferences.rememberedUdis.first().getValue("75056").name)
    }

    @Test
    fun `alerts are off by default, weekly`() = runBlocking {
        val preferences = preferences(storeFile())
        assertFalse(preferences.alertNewAnalysis.first())
        assertFalse(preferences.alertUnfavourable.first())
        assertEquals(AppPreferences.DEFAULT_ALERT_WEEKS, preferences.alertPeriodWeeks.first())
        assertEquals(1, preferences.alertPeriodWeeks.first())
    }

    @Test
    fun `alert settings are remembered across a restart`() = runBlocking {
        val file = storeFile()
        preferences(file).apply {
            setAlertNewAnalysis(true)
            setAlertUnfavourable(true)
            setAlertPeriodWeeks(4)
        }
        closeAll()

        val preferences = preferences(file)
        assertTrue(preferences.alertNewAnalysis.first())
        assertTrue(preferences.alertUnfavourable.first())
        assertEquals(4, preferences.alertPeriodWeeks.first())

        preferences.setAlertNewAnalysis(false)
        assertFalse(preferences.alertNewAnalysis.first())
        assertTrue(preferences.alertUnfavourable.first())
    }

    @Test
    fun `an alert period outside the choices falls back to the default`() = runBlocking {
        val store = store(storeFile())
        val preferences = DataStoreAppPreferences(store)
        preferences.setAlertPeriodWeeks(2)
        assertEquals(2, preferences.alertPeriodWeeks.first())
        preferences.setAlertPeriodWeeks(3)
        assertEquals(AppPreferences.DEFAULT_ALERT_WEEKS, preferences.alertPeriodWeeks.first())

        // A value written by another version of the application.
        store.edit { it[intPreferencesKey("alert_period_weeks")] = 52 }
        assertEquals(AppPreferences.DEFAULT_ALERT_WEEKS, preferences.alertPeriodWeeks.first())
    }

    @Test
    fun `the recent units survive a restart, most recent first`() = runBlocking {
        val file = storeFile()
        val orleans = RecentUnit("45234", "Orléans", "045000123", "ORLEANS")
        val paris = RecentUnit("75056", "Paris", "075000456", "PARIS SUD")
        preferences(file).run {
            addRecentUnit(orleans)
            addRecentUnit(paris)
            addRecentUnit(orleans)
        }
        closeAll()

        val preferences = preferences(file)
        assertEquals(listOf(orleans, paris), preferences.recentUnits.first())
        preferences.removeRecentUnit("45234", "045000123")
        assertEquals(listOf(paris), preferences.recentUnits.first())
    }

    @Test
    fun `the recent units are kept to the limit and are not remembered units`() = runBlocking {
        val preferences = preferences(storeFile())
        val units = (1..AppPreferences.RECENT_LIMIT + 2).map {
            RecentUnit("0100$it", "Commune $it", "00100$it", "UDI $it")
        }
        units.forEach { preferences.addRecentUnit(it) }

        assertEquals(
            units.reversed().take(AppPreferences.RECENT_LIMIT),
            preferences.recentUnits.first(),
        )
        assertTrue(preferences.rememberedUdis.first().isEmpty())
    }
}

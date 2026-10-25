package io.github.mgdx.potable.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Clear cached data" forgets the units of the communes not followed (audit V5). */
class ForgetRememberedUdisTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun closeStore() = scope.cancel()

    private val store by lazy {
        val file = folder.newFile("preferences.preferences_pb").apply { delete() }
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private val preferences by lazy { DataStoreAppPreferences(store) }

    private suspend fun storedKeys(): Set<String> =
        store.data.first().asMap().keys.mapTo(mutableSetOf()) { it.name }

    @Test
    fun forgetsEveryRememberedUdiButTheKeptCommunes() = runBlocking {
        preferences.rememberUdi("45234", "045000123")
        preferences.rememberUdi("75056", "075000221")
        preferences.rememberUdi("2A004", "020000123")

        preferences.forgetRememberedUdis(keep = setOf("2A004"))

        assertNull(preferences.rememberedUdi("45234"))
        assertNull(preferences.rememberedUdi("75056"))
        assertEquals("020000123", preferences.rememberedUdi("2A004"))
    }

    @Test
    fun forgetsTheUdiOfOneCommuneOnly() = runBlocking {
        preferences.rememberUdi("45234", "045000123", "ORLEANS")
        preferences.rememberUdi("75056", "075000221", "PARIS CENTRE")

        preferences.forgetRememberedUdi("45234")
        preferences.forgetRememberedUdi("99999")

        assertNull(preferences.rememberedUdi("45234"))
        assertEquals("075000221", preferences.rememberedUdi("75056"))
        // The name goes with the code, and only that commune's.
        assertEquals(setOf("udi_75056", "udiname_75056"), storedKeys())
        assertEquals(
            mapOf("75056" to RememberedUdi("075000221", "PARIS CENTRE")),
            preferences.rememberedUdis.first(),
        )
    }

    @Test
    fun keepsTheOtherSettings() = runBlocking {
        preferences.acceptDisclaimer()
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setRetentionYears(5)
        preferences.setHistoryYears(10)
        preferences.setAlertNewAnalysis(true)
        preferences.setAlertPeriodWeeks(4)
        preferences.rememberUdi("45234", "045000123")

        preferences.forgetRememberedUdis(keep = emptySet())

        assertNull(preferences.rememberedUdi("45234"))
        assertTrue(preferences.disclaimerAccepted.first())
        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertEquals(5, preferences.retentionYears.first())
        assertEquals(10, preferences.historyYears.first())
        assertTrue(preferences.alertNewAnalysis.first())
        assertEquals(4, preferences.alertPeriodWeeks.first())
    }
}

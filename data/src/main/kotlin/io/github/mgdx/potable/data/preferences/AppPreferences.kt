package io.github.mgdx.potable.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The user's settings, kept on the device only (SPEC §7, §10). */
interface AppPreferences {
    /** Whether the first-launch notice (SPEC §14) has been read and acknowledged. */
    val disclaimerAccepted: Flow<Boolean>

    suspend fun acceptDisclaimer()

    // --- Follows and remembered units ---

    /** Light, dark or system theme (SPEC §5 F8). Default [ThemeMode.SYSTEM]. */
    val themeMode: Flow<ThemeMode> get() = flowOf(ThemeMode.SYSTEM)

    suspend fun setThemeMode(mode: ThemeMode) {}

    /** History window shown by default, in years (SPEC §5 F5). Default 3. */
    val historyYears: Flow<Int> get() = flowOf(DEFAULT_YEARS)

    suspend fun setHistoryYears(years: Int) {}

    /** Cache retention, in years (SPEC §7). Default 3. */
    val retentionYears: Flow<Int> get() = flowOf(DEFAULT_YEARS)

    suspend fun setRetentionYears(years: Int) {}

    /** The unit last chosen for a commune with several (SPEC §5 F2), or null. */
    suspend fun rememberedUdi(communeCode: String): String? = null

    suspend fun rememberUdi(communeCode: String, udiCode: String) {}

    /**
     * Remembers the unit chosen for a commune with its name as published, so
     * that the search can offer it by name without a request.
     */
    suspend fun rememberUdi(communeCode: String, udiCode: String, udiName: String) =
        rememberUdi(communeCode, udiCode)

    /** Every remembered unit, by commune code. */
    val rememberedUdis: Flow<Map<String, RememberedUdi>> get() = flowOf(emptyMap())

    /**
     * Forgets the unit remembered for every commune whose code is not in
     * [keep] (the followed ones): "Clear cached data" leaves no trace of the
     * communes looked at without being followed.
     */
    suspend fun forgetRememberedUdis(keep: Set<String>) {}

    // --- Alerts ---

    /** Notify each new analysis of a followed unit (SPEC §5 F7). Default off. */
    val alertNewAnalysis: Flow<Boolean> get() = flowOf(false)

    suspend fun setAlertNewAnalysis(enabled: Boolean) {}

    /** Notify only unfavourable conclusions (SPEC §5 F7). Default off. */
    val alertUnfavourable: Flow<Boolean> get() = flowOf(false)

    suspend fun setAlertUnfavourable(enabled: Boolean) {}

    /** Period of the alert check, in weeks (SPEC §5 F7). Default 1. */
    val alertPeriodWeeks: Flow<Int> get() = flowOf(DEFAULT_ALERT_WEEKS)

    suspend fun setAlertPeriodWeeks(weeks: Int) {}

    companion object {
        const val DEFAULT_YEARS = 3

        /** Values offered by the settings screen for both windows. */
        val YEAR_CHOICES = listOf(1, 3, 5, 10)

        const val DEFAULT_ALERT_WEEKS = 1

        /** Values offered for the alert period, in weeks. */
        val ALERT_WEEK_CHOICES = listOf(1, 2, 4)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The unit remembered for a commune; [name] is null for a choice stored
 * before names were kept.
 */
data class RememberedUdi(val code: String, val name: String?)

/** [AppPreferences] stored in a Preferences DataStore. Use one instance per process. */
class DataStoreAppPreferences internal constructor(private val store: DataStore<Preferences>) :
    AppPreferences {
    constructor(context: Context) : this(context.applicationContext.preferencesStore)

    // An unreadable file reads as defaults: the notice is shown again and the
    // settings fall back to theirs, rather than the application failing.
    private val data: Flow<Preferences> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override val disclaimerAccepted: Flow<Boolean> = data
        .map { it[DISCLAIMER_ACCEPTED] ?: false }
        .distinctUntilChanged()

    override suspend fun acceptDisclaimer() {
        store.edit { it[DISCLAIMER_ACCEPTED] = true }
    }

    override val themeMode: Flow<ThemeMode> = data
        .map { prefs ->
            ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.SYSTEM
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    override val historyYears: Flow<Int> = years(HISTORY_YEARS)

    override suspend fun setHistoryYears(years: Int) = setYears(HISTORY_YEARS, years)

    override val retentionYears: Flow<Int> = years(RETENTION_YEARS)

    override suspend fun setRetentionYears(years: Int) = setYears(RETENTION_YEARS, years)

    override suspend fun rememberedUdi(communeCode: String): String? =
        data.first()[udiKey(communeCode)]

    override suspend fun rememberUdi(communeCode: String, udiCode: String) {
        store.edit {
            it[udiKey(communeCode)] = udiCode
            // A name kept for another unit would no longer match.
            it.remove(udiNameKey(communeCode))
        }
    }

    override suspend fun rememberUdi(communeCode: String, udiCode: String, udiName: String) {
        store.edit {
            it[udiKey(communeCode)] = udiCode
            it[udiNameKey(communeCode)] = udiName
        }
    }

    override val rememberedUdis: Flow<Map<String, RememberedUdi>> = data
        .map { prefs ->
            prefs.asMap().mapNotNull { (key, value) ->
                val communeCode = key.name.takeIf { it.startsWith(UDI_PREFIX) }
                    ?.removePrefix(UDI_PREFIX)
                val code = value as? String
                if (communeCode == null || code == null) {
                    null
                } else {
                    communeCode to RememberedUdi(code, prefs[udiNameKey(communeCode)])
                }
            }.toMap()
        }
        .distinctUntilChanged()

    override suspend fun forgetRememberedUdis(keep: Set<String>) {
        store.edit { prefs ->
            prefs.asMap().keys
                .map { it.name }
                .filter { name ->
                    listOf(UDI_PREFIX, UDI_NAME_PREFIX).any { prefix ->
                        name.startsWith(prefix) && name.removePrefix(prefix) !in keep
                    }
                }
                .forEach { prefs.remove(stringPreferencesKey(it)) }
        }
    }

    override val alertNewAnalysis: Flow<Boolean> = flag(ALERT_NEW_ANALYSIS)

    override suspend fun setAlertNewAnalysis(enabled: Boolean) {
        store.edit { it[ALERT_NEW_ANALYSIS] = enabled }
    }

    override val alertUnfavourable: Flow<Boolean> = flag(ALERT_UNFAVOURABLE)

    override suspend fun setAlertUnfavourable(enabled: Boolean) {
        store.edit { it[ALERT_UNFAVOURABLE] = enabled }
    }

    /** A value outside [AppPreferences.ALERT_WEEK_CHOICES] reads as the default. */
    override val alertPeriodWeeks: Flow<Int> = data
        .map { it[ALERT_PERIOD_WEEKS].orDefaultWeeks() }
        .distinctUntilChanged()

    override suspend fun setAlertPeriodWeeks(weeks: Int) {
        store.edit { it[ALERT_PERIOD_WEEKS] = weeks.orDefaultWeeks() }
    }

    /** Alerts are off until the user switches them on (SPEC §5 F7). */
    private fun flag(key: Preferences.Key<Boolean>): Flow<Boolean> = data
        .map { it[key] ?: false }
        .distinctUntilChanged()

    private fun Int?.orDefaultWeeks(): Int =
        this?.takeIf { it in AppPreferences.ALERT_WEEK_CHOICES }
            ?: AppPreferences.DEFAULT_ALERT_WEEKS

    /** A value outside [AppPreferences.YEAR_CHOICES] reads as the default. */
    private fun years(key: Preferences.Key<Int>): Flow<Int> = data
        .map { it[key].orDefaultYears() }
        .distinctUntilChanged()

    private suspend fun setYears(key: Preferences.Key<Int>, years: Int) {
        store.edit { it[key] = years.orDefaultYears() }
    }

    private fun Int?.orDefaultYears(): Int =
        this?.takeIf { it in AppPreferences.YEAR_CHOICES } ?: AppPreferences.DEFAULT_YEARS

    private companion object {
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HISTORY_YEARS = intPreferencesKey("history_years")
        val RETENTION_YEARS = intPreferencesKey("retention_years")
        val ALERT_NEW_ANALYSIS = booleanPreferencesKey("alert_new_analysis")
        val ALERT_UNFAVOURABLE = booleanPreferencesKey("alert_unfavourable")
        val ALERT_PERIOD_WEEKS = intPreferencesKey("alert_period_weeks")

        /** One key per commune with several units: only its code and the chosen unit's. */
        fun udiKey(communeCode: String) = stringPreferencesKey("$UDI_PREFIX$communeCode")

        /** The name of the unit under [udiKey], as published; absent for older choices. */
        fun udiNameKey(communeCode: String) = stringPreferencesKey("$UDI_NAME_PREFIX$communeCode")

        const val UDI_PREFIX = "udi_"

        /** Does not start with [UDI_PREFIX]: no name key reads as a commune's unit. */
        const val UDI_NAME_PREFIX = "udiname_"
    }
}

/** A DataStore must be a singleton per file: the delegate guarantees it. */
private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "preferences",
)

package io.github.mgdx.potable.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

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

    /** Forgets the unit remembered for [communeCode], code and name, if any (its cache was cleared). */
    suspend fun forgetRememberedUdi(communeCode: String) {}

    // --- Recent units ---

    /**
     * The last opened units, most recent first, distinct by (communeCode,
     * udiCode), at most [RECENT_LIMIT] (SPEC §5 F1).
     */
    val recentUnits: Flow<List<RecentUnit>> get() = flowOf(emptyList())

    /** Puts [unit] at the top of [recentUnits], moving it if already there, and trims the list. */
    suspend fun addRecentUnit(unit: RecentUnit) {}

    /** Removes the unit [udiCode] of [communeCode] from [recentUnits], if there. */
    suspend fun removeRecentUnit(communeCode: String, udiCode: String) {}

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

    /**
     * When the alert check last ended having asked Hub'Eau for every followed
     * unit; null until it has once.
     */
    val lastAlertCheck: Flow<Instant?> get() = flowOf(null)

    suspend fun setLastAlertCheck(at: Instant) {}

    /**
     * When the alert check last ended without having asked Hub'Eau for every
     * followed unit (network, server); null if it never has.
     */
    val lastAlertFailure: Flow<Instant?> get() = flowOf(null)

    suspend fun setLastAlertFailure(at: Instant) {}

    companion object {
        const val DEFAULT_YEARS = 3

        /** Values offered by the settings screen for both windows. */
        val YEAR_CHOICES = listOf(1, 3, 5, 10)

        const val DEFAULT_ALERT_WEEKS = 1

        /** Values offered for the alert period, in weeks. */
        val ALERT_WEEK_CHOICES = listOf(1, 2, 4)

        /** Length of the search's "recent" list. */
        const val RECENT_LIMIT = 5
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The unit remembered for a commune; [name] is null for a choice stored
 * before names were kept.
 */
data class RememberedUdi(val code: String, val name: String?)

/**
 * A unit whose sheet was opened, for the search's "recent" list.
 * [communeCode] is the code Hub'Eau publishes the commune under.
 */
data class RecentUnit(
    val communeCode: String,
    val communeName: String,
    val udiCode: String,
    val udiName: String,
)

/**
 * [recent] with [unit] put first: an entry for the same commune and unit is
 * replaced (its names may have changed), and the list is cut to [limit].
 */
fun withRecentUnit(
    recent: List<RecentUnit>,
    unit: RecentUnit,
    limit: Int = AppPreferences.RECENT_LIMIT,
): List<RecentUnit> =
    (listOf(unit) + recent.filterNot { it.sameUnitAs(unit.communeCode, unit.udiCode) })
        .take(limit)

/** [recent] without the unit [udiCode] of [communeCode]. */
fun withoutRecentUnit(
    recent: List<RecentUnit>,
    communeCode: String,
    udiCode: String,
): List<RecentUnit> = recent.filterNot { it.sameUnitAs(communeCode, udiCode) }

private fun RecentUnit.sameUnitAs(communeCode: String, udiCode: String) =
    this.communeCode == communeCode && this.udiCode == udiCode

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

    override suspend fun forgetRememberedUdi(communeCode: String) {
        store.edit {
            it.remove(udiKey(communeCode))
            it.remove(udiNameKey(communeCode))
        }
    }

    override val recentUnits: Flow<List<RecentUnit>> = data
        .map(::readRecent)
        .distinctUntilChanged()

    override suspend fun addRecentUnit(unit: RecentUnit) {
        store.edit { writeRecent(it, withRecentUnit(readRecent(it), unit)) }
    }

    override suspend fun removeRecentUnit(communeCode: String, udiCode: String) {
        store.edit { writeRecent(it, withoutRecentUnit(readRecent(it), communeCode, udiCode)) }
    }

    /** One key per position, from 0; an unreadable value is skipped. */
    private fun readRecent(prefs: Preferences): List<RecentUnit> =
        (0 until AppPreferences.RECENT_LIMIT).mapNotNull { index ->
            prefs[recentKey(index)]?.split(FIELD_SEPARATOR)
                ?.takeIf { it.size == RECENT_FIELDS }
                ?.let { (communeCode, communeName, udiCode, udiName) ->
                    RecentUnit(communeCode, communeName, udiCode, udiName)
                }
        }

    private fun writeRecent(prefs: MutablePreferences, recent: List<RecentUnit>) {
        (0 until AppPreferences.RECENT_LIMIT).forEach { prefs.remove(recentKey(it)) }
        recent.take(AppPreferences.RECENT_LIMIT).forEachIndexed { index, unit ->
            prefs[recentKey(index)] = listOf(
                unit.communeCode,
                unit.communeName,
                unit.udiCode,
                unit.udiName,
            ).joinToString(FIELD_SEPARATOR) { it.replace(FIELD_SEPARATOR, "") }
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

    override val lastAlertCheck: Flow<Instant?> = instant(LAST_ALERT_CHECK)

    override suspend fun setLastAlertCheck(at: Instant) {
        store.edit { it[LAST_ALERT_CHECK] = at.toEpochMilli() }
    }

    override val lastAlertFailure: Flow<Instant?> = instant(LAST_ALERT_FAILURE)

    override suspend fun setLastAlertFailure(at: Instant) {
        store.edit { it[LAST_ALERT_FAILURE] = at.toEpochMilli() }
    }

    private fun instant(key: Preferences.Key<Long>): Flow<Instant?> = data
        .map { prefs -> prefs[key]?.let(Instant::ofEpochMilli) }
        .distinctUntilChanged()

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
        val LAST_ALERT_CHECK = longPreferencesKey("last_alert_check")
        val LAST_ALERT_FAILURE = longPreferencesKey("last_alert_failure")

        /** One key per commune with several units: only its code and the chosen unit's. */
        fun udiKey(communeCode: String) = stringPreferencesKey("$UDI_PREFIX$communeCode")

        /** The name of the unit under [udiKey], as published; absent for older choices. */
        fun udiNameKey(communeCode: String) = stringPreferencesKey("$UDI_NAME_PREFIX$communeCode")

        const val UDI_PREFIX = "udi_"

        /**
         * The unit at [index] of the "recent" list. Starts with neither
         * [UDI_PREFIX] nor [UDI_NAME_PREFIX]: never read as a remembered unit.
         */
        fun recentKey(index: Int) = stringPreferencesKey("recent_$index")

        /** Between the fields of a recent unit: a control character no code nor name holds. */
        const val FIELD_SEPARATOR = "\u001F"

        const val RECENT_FIELDS = 4

        /** Does not start with [UDI_PREFIX]: no name key reads as a commune's unit. */
        const val UDI_NAME_PREFIX = "udiname_"
    }
}

/** A DataStore must be a singleton per file: the delegate guarantees it. */
private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "preferences",
)

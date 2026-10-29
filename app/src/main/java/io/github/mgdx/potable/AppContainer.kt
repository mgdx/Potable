package io.github.mgdx.potable

import android.content.Context
import io.github.mgdx.potable.alert.AlertNotifier
import io.github.mgdx.potable.alert.AlertRun
import io.github.mgdx.potable.alert.AlertScheduler
import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.alert.SystemNotificationAccess
import io.github.mgdx.potable.alert.WorkManagerAlertWork
import io.github.mgdx.potable.data.AssetCommuneRepository
import io.github.mgdx.potable.data.CacheJanitor
import io.github.mgdx.potable.data.CachingSampleRepository
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.RemoteSampleRepository
import io.github.mgdx.potable.data.RemoteWaterRepository
import io.github.mgdx.potable.data.SampleRepository
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.alert.AlertChecker
import io.github.mgdx.potable.data.backup.FollowsBackup
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.follow.RoomFollowRepository
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.DataStoreAppPreferences
import io.github.mgdx.potable.location.DeviceLocator
import io.github.mgdx.potable.location.Locator
import okhttp3.OkHttpClient

/**
 * Manual dependency injection (SPEC §8): every long-lived object is built
 * here, once. Repositories are created lazily so that building the container
 * costs nothing at startup.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** The single HTTP client of the process; it only ever talks to Hub'Eau. */
    private val http: OkHttpClient by lazy { OkHttpClient() }

    private val hubEau: HubEauClient by lazy { HubEauClient(http) }

    val communes: CommuneRepository by lazy { AssetCommuneRepository(appContext) }

    /** The only instance of the Room database (follows and their cache). */
    private val database: PotableDatabase by lazy { PotableDatabase.open(appContext) }

    val water: WaterRepository by lazy {
        CachingWaterRepository(RemoteWaterRepository(hubEau), database, preferences)
    }

    val follows: FollowRepository by lazy { RoomFollowRepository(database) }

    /** The only instance: a DataStore file must have a single owner per process. */
    val preferences: AppPreferences by lazy { DataStoreAppPreferences(appContext) }

    val locator: Locator by lazy { DeviceLocator(appContext) }

    val notificationAccess: NotificationAccess by lazy { SystemNotificationAccess(appContext) }

    val alertNotifier: AlertNotifier by lazy { AlertNotifier(appContext, notificationAccess) }

    /** One alert check (SPEC §5 F7); [alertChecker] is only built when a check needs it. */
    val alertRun: AlertRun by lazy { AlertRun(preferences, { alertChecker }, alertNotifier) }

    /** Plans or cancels the periodic alert check as the settings change. */
    val alertScheduler: AlertScheduler by lazy {
        AlertScheduler(preferences, { alertChecker }, WorkManagerAlertWork(appContext))
    }

    /** History of a unit (SPEC §5 F5). */
    val history: HistoryRepository by lazy { HistoryRepository(hubEau, database, preferences) }

    /** Any sample of a unit by its code, from the history (SPEC §5 F4, F5), cached (SPEC §7). */
    val samples: SampleRepository by lazy {
        CachingSampleRepository(RemoteSampleRepository(hubEau), database, preferences)
    }

    /** Keeps the cache to the followed and recent units (SPEC §7). */
    val cacheJanitor: CacheJanitor by lazy { CacheJanitor(follows, preferences, water) }

    /** Checks the followed units for new samples (SPEC §5 F7). */
    val alertChecker: AlertChecker by lazy { AlertChecker(hubEau, follows, water) }

    /** Export and import of the follows; the baseline is the sheet's ([alertChecker]). */
    val backup: FollowsBackup by lazy {
        FollowsBackup(
            follows,
            preferences,
            baseline = { alertChecker.setBaseline(it) },
            applyRetention = { water.purgeOlderThanRetention() },
        )
    }
}

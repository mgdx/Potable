package io.github.mgdx.potable.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.mgdx.potable.PotableApplication
import io.github.mgdx.potable.alert.AlertWorker
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Acceptance tools for the alerts, in debug builds only (`docs/test-alerts.md`):
 *
 * - `--es action age` moves the last seen sample of every follow one day
 *   back (or to 1970 when there is none yet), so that the next check finds
 *   the current latest sample new;
 * - `--es action run` runs the alert check now, once, without waiting for
 *   the period nor for a network (the check skips the units it cannot reach).
 *
 * It shows nothing and finishes at once; the outcome is in logcat, tag
 * [TAG]. Exported so that `adb shell am start` reaches it: the release
 * build has neither this class nor its manifest entry.
 */
class DebugAlertsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PotableApplication).container
        when (val action = intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_AGE -> {
                val water = container.water as? CachingWaterRepository
                if (water == null) {
                    Log.w(TAG, "age: the water repository keeps no last seen date")
                } else {
                    val dates = CacheLastSeenDates(water)
                    scope.launch { ageLastSeen(container.follows, dates) }
                }
            }
            ACTION_RUN -> {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    RUN_NOW_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<AlertWorker>().build(),
                )
                Log.i(TAG, "run: alert check enqueued")
            }
            else -> Log.w(TAG, "unknown action '$action'; use --es action age|run")
        }
        finish()
    }

    private companion object {
        const val TAG = "PotableDebug"
        const val EXTRA_ACTION = "action"
        const val ACTION_AGE = "age"
        const val ACTION_RUN = "run"
        const val RUN_NOW_NAME = "alert-check-debug"

        /** Outlives the activity, which finishes at once; ends with the process. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun ageLastSeen(follows: FollowRepository, dates: LastSeenDates) {
            val all = follows.follows.first()
            all.forEach { follow ->
                val seen = dates.get(follow.communeCode, follow.udiCode)
                dates.set(follow.communeCode, follow.udiCode, aged(seen))
            }
            Log.i(TAG, "age: last seen date moved back for ${all.size} follow(s)")
        }
    }
}

/** One day before [seen], or the epoch when nothing was seen yet. */
internal fun aged(seen: Instant?): Instant = seen?.minus(Duration.ofDays(1)) ?: Instant.EPOCH

/**
 * The date of the newest sample the alert task saw for a unit. Behind an
 * interface so that the tool does not depend on where `:data` keeps it.
 */
interface LastSeenDates {
    suspend fun get(communeCode: String, udiCode: String): Instant?

    suspend fun set(communeCode: String, udiCode: String, date: Instant?)
}

/** [LastSeenDates] kept by the cache (`synchro.dernier_prelevement_vu`), public API of `:data`. */
class CacheLastSeenDates(private val water: CachingWaterRepository) : LastSeenDates {
    override suspend fun get(communeCode: String, udiCode: String): Instant? =
        water.lastSeenSampleDate(communeCode, udiCode)

    override suspend fun set(communeCode: String, udiCode: String, date: Instant?) =
        water.setLastSeenSampleDate(communeCode, udiCode, date)
}

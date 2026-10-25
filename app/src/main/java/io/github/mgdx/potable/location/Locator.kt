package io.github.mgdx.potable.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** What came of asking the device where it is. */
sealed interface LocationOutcome {
    /**
     * A coarse position. Deliberately not a data class, so that it has no
     * `toString` that could print the coordinates (SPEC §10).
     */
    class Position(val latitude: Double, val longitude: Double) : LocationOutcome

    /** The coarse location permission is not granted. */
    data object PermissionMissing : LocationOutcome

    /** Location is turned off in the system settings. */
    data object Disabled : LocationOutcome

    /** No position could be obtained. */
    data object Unavailable : LocationOutcome
}

/** One-shot access to the device's coarse position. */
fun interface Locator {
    /** May suspend until a position comes; the caller bounds the wait. */
    suspend fun locate(): LocationOutcome
}

/**
 * [Locator] over the framework's [LocationManager], without Google Play
 * Services: network provider, or passive when it is off. Nothing is stored or
 * logged; the position is only handed back to the caller.
 */
class DeviceLocator(context: Context) : Locator {
    private val app = context.applicationContext

    override suspend fun locate(): LocationOutcome {
        val granted = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return LocationOutcome.PermissionMissing
        val manager = app.getSystemService(LocationManager::class.java)
            ?: return LocationOutcome.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationOutcome.Disabled
        val location = try {
            position(manager)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the request.
            return LocationOutcome.PermissionMissing
        }
        return location?.let { LocationOutcome.Position(it.latitude, it.longitude) }
            ?: LocationOutcome.Unavailable
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private suspend fun position(manager: LocationManager): Location? {
        val provider = if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else {
            LocationManager.PASSIVE_PROVIDER
        }
        return recentLastKnown(manager, provider) ?: currentLocation(manager, provider)
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun recentLastKnown(manager: LocationManager, provider: String): Location? =
        manager.getLastKnownLocation(provider)?.takeIf { location ->
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            age in 0..MAX_LAST_KNOWN_AGE_NANOS
        }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(app),
            ) { location -> continuation.resume(location) }
        }

    private companion object {
        /** A cached position this recent is as good as a new one to find a commune. */
        val MAX_LAST_KNOWN_AGE_NANOS = TimeUnit.MINUTES.toNanos(10)
    }
}

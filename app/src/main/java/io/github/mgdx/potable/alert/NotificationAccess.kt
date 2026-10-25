package io.github.mgdx.potable.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Whether the application may show notifications, and how to ask for it. */
interface NotificationAccess {
    /** True when notifications can be shown now. */
    fun canPost(): Boolean

    /**
     * True when the system can ask the user (`POST_NOTIFICATIONS`, Android
     * 13+). Below, notifications are allowed at install and can only be
     * turned back on from the system settings.
     */
    val canRequest: Boolean
}

class SystemNotificationAccess(context: Context) : NotificationAccess {
    private val appContext = context.applicationContext

    override fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    override val canRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

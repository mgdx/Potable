package io.github.mgdx.potable.alert

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.alert.AlertEvent
import io.github.mgdx.potable.ui.common.apiParagraph
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sheet.formatSampleDate
import java.util.Locale

/** Shows the notifications of the alert check. */
interface AlertPoster {
    /** True when a notification can be shown now. */
    fun canPost(): Boolean

    /** One notification per unit; a newer one replaces the previous one of the same unit. */
    fun post(events: List<AlertEvent>)
}

/**
 * Posts the alerts as system notifications (SPEC §5 F7), in two channels of
 * default importance. The content is factual: the commune and its unit, then
 * the date of the sample or, when unfavourable, its published conclusion
 * (SPEC §14). Each follow has its own notification id, its own tap target.
 *
 * The notifications of the units form one group. Once two or more of them
 * are shown, an explicit summary heads the group: touching it opens "My
 * communes", where Android's automatic group would open the latest unit.
 */
class AlertNotifier(
    context: Context,
    private val access: NotificationAccess = SystemNotificationAccess(context),
) : AlertPoster {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    /** Creates or updates the channels; their names follow the language. Idempotent. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                channel(
                    AlertChannel.NEW_ANALYSIS,
                    R.string.alert_new_analyses,
                    R.string.alert_channel_new_description,
                ),
                channel(
                    AlertChannel.UNFAVOURABLE,
                    R.string.alert_channel_unfavourable,
                    R.string.alert_channel_unfavourable_description,
                ),
            ),
        )
    }

    override fun canPost(): Boolean = access.canPost()

    override fun post(events: List<AlertEvent>) {
        if (events.isEmpty()) return
        // Checked here again: the permission can be revoked at any time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannels()
        // The newest event of each unit, if the check returned several.
        events.groupBy {
            it.follow.id
        }.values.map { it.maxBy { event -> event.sample.date } }.forEach { event ->
            manager.notify(TAG, event.follow.id.toInt(), notification(event))
        }
        postSummary()
    }

    /**
     * The summary of the group, when it holds two notifications or more
     * (those just posted and those still shown), in the unfavourable channel
     * if one of them is. Touching it dismisses it with the whole group.
     * Called by [post] once the permission is checked.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postSummary() {
        val shown = manager.activeNotifications.filter {
            it.tag == TAG &&
                it.notification.group == GROUP &&
                it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
        }
        if (shown.size < 2) return
        val channel = if (shown.any { it.notification.channelId == AlertChannel.UNFAVOURABLE.id }) {
            AlertChannel.UNFAVOURABLE
        } else {
            AlertChannel.NEW_ANALYSIS
        }
        val title = appContext.getString(R.string.alert_summary_title)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        shown.mapNotNull {
            it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)
        }.forEach { style.addLine(it) }
        val tap = PendingIntent.getActivity(
            appContext,
            SUMMARY_ID,
            followsIntent(appContext),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val summary = NotificationCompat.Builder(appContext, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setStyle(style)
            .setGroup(GROUP)
            .setGroupSummary(true)
            // The units' notifications already alerted: the summary stays silent.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel.id))
            .build()
        manager.notify(SUMMARY_TAG, SUMMARY_ID, summary)
    }

    private fun notification(event: AlertEvent): android.app.Notification {
        val follow = event.follow
        val locale = locale()
        // The commune alone: the unit's name, an operator's code, would crowd
        // the title. bidiWrap starts it with a mark of the language's direction,
        // although it is API data. The conclusion, shown alone, keeps its own
        // direction (SPEC §11.2).
        val title = bidiWrap(follow.communeName, locale)
        val date = formatSampleDate(event.sample.date, locale)
        val conclusion = if (event.unfavourable) {
            conclusionText(event.sample.conclusion)?.let { apiParagraph(it, locale) }
        } else {
            null
        }
        val text = conclusion ?: appContext.getString(R.string.alert_notification_new, date)
        val tap = PendingIntent.getActivity(
            appContext,
            follow.id.toInt(),
            sheetIntent(appContext, follow),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(appContext, AlertChannel.of(event).id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // The conclusion is published text: its date goes in the header.
            .setSubText(
                if (conclusion !=
                    null
                ) {
                    appContext.getString(R.string.alert_notification_date, date)
                } else {
                    null
                },
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tap)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(AlertChannel.of(event).id))
            .build()
    }

    /**
     * What a locked screen that hides sensitive content shows instead: no
     * commune, no unit, no conclusion, which would tell where the person
     * lives (audit I2). Not secret: the alert itself stays visible.
     */
    private fun publicVersion(channelId: String): android.app.Notification =
        NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.alert_summary_title))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun channel(channel: AlertChannel, name: Int, description: Int) =
        NotificationChannelCompat.Builder(channel.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(appContext.getString(name))
            .setDescription(appContext.getString(description))
            .build()

    /** The language of the app (per-app language on Android 13+), read at each notification. */
    private fun locale(): Locale = appContext.resources.configuration.locales[0]

    companion object {
        /** Tag of the units' notifications; their id is the follow's. */
        const val TAG = "alert"

        /** The group of the units' notifications. */
        const val GROUP = "io.github.mgdx.potable.ALERTS"

        /** Tag and id of the summary: no follow has the id 0 (Room starts at 1). */
        const val SUMMARY_TAG = "alert-summary"
        const val SUMMARY_ID = 0
    }
}

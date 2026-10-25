package io.github.mgdx.potable.alert

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import io.github.mgdx.potable.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlertNotifierTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val manager =
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notifier = AlertNotifier(
        application,
        object : NotificationAccess {
            override fun canPost() = true

            override val canRequest = true
        },
    )

    @Before
    fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun active(): List<StatusBarNotification> = manager.activeNotifications.toList()

    private fun StatusBarNotification.isSummary() =
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    @Test
    fun `a single notification has no summary`() {
        notifier.post(listOf(event(1, unfavourable = false)))
        val shown = active()
        assertEquals(1, shown.size)
        assertEquals(AlertNotifier.GROUP, shown.single().notification.group)
        assertTrue(shown.none { it.isSummary() })
    }

    @Test
    fun `several notifications are headed by a summary that opens My communes`() {
        notifier.post(listOf(event(1, unfavourable = false), event(2, unfavourable = false)))
        val shown = active()
        assertEquals(3, shown.size)
        val summary = shown.single { it.isSummary() }
        assertEquals(AlertNotifier.SUMMARY_TAG, summary.tag)
        assertEquals(AlertNotifier.GROUP, summary.notification.group)
        assertTrue(shown.all { it.notification.group == AlertNotifier.GROUP })
        assertEquals(
            application.getString(R.string.alert_summary_title),
            summary.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(AlertChannel.NEW_ANALYSIS.id, summary.notification.channelId)
        val tap = shadowOf(summary.notification.contentIntent).savedIntent
        assertEquals(FollowsTarget, tap.alertTarget())
    }

    @Test
    fun `the summary also counts the notifications still shown`() {
        notifier.post(listOf(event(1, unfavourable = false)))
        notifier.post(listOf(event(2, unfavourable = true)))
        val shown = active()
        assertEquals(3, shown.size)
        val summary = shown.single { it.isSummary() }
        // One of them is unfavourable: the summary is in that channel.
        assertEquals(AlertChannel.UNFAVOURABLE.id, summary.notification.channelId)
    }

    @Test
    fun `a unit's notification still opens its sheet`() {
        notifier.post(listOf(event(1, unfavourable = false), event(2, unfavourable = false)))
        val unit = active().first { !it.isSummary() }
        val target = shadowOf(unit.notification.contentIntent).savedIntent.alertTarget()
        assertTrue(target is SheetTarget)
    }

    @Test
    fun `the locked screen shows a version without any commune`() {
        notifier.post(listOf(event(1, unfavourable = true), event(2, unfavourable = false)))
        val shown = active()
        assertEquals(3, shown.size)
        val generic = application.getString(R.string.alert_summary_title)
        for (posted in shown.map { it.notification }) {
            assertEquals(NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
            val public = posted.publicVersion
            assertNotNull(public)
            val title = public.extras.getCharSequence(Notification.EXTRA_TITLE)
            assertEquals(generic, title.toString())
            assertEquals(posted.channelId, public.channelId)
            val texts = listOf(
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
            )
            assertTrue(texts.all { public.extras.getCharSequence(it) == null })
            assertNull(public.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES))
        }
    }
}

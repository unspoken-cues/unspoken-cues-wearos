package com.unspokencues.wear.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AnnouncementAlertsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val alerts = AnnouncementAlerts(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private val first = AlertCursor(UUID.randomUUID(), 1_000)

    @Before fun prepare() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        alerts.disable()
        alerts.createChannel()
    }

    @After fun reset() { alerts.disable() }

    @Test fun schedulesInBackgroundAndPostsOneDiscreetNativeNotification() {
        alerts.enable()
        val periodic = scheduler.getPendingJob(PERIODIC_JOB)!!
        assertTrue(periodic.isPersisted)
        assertTrue(periodic.isPeriodic)
        assertEquals(POLL_INTERVAL_MS, periodic.intervalMillis)
        val channel = manager.getNotificationChannel(ALERT_CHANNEL)
        assertTrue(channel.shouldVibrate())
        assertNull(channel.sound)
        val empty = alerts.read()!!
        alerts.receive(empty, first)
        assertTrue(manager.activeNotifications.none { it.id == NOTIFICATION_ID })
        val baseline = alerts.read()!!
        val update = first.copy(micros = 2_000)
        alerts.receive(baseline, update)
        val posted = awaitNotification().notification
        assertEquals(ALERT_CHANNEL, posted.channelId)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("Event update", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Check announcements on your phone.", posted.extras.getString(Notification.EXTRA_TEXT))
        val reopened = AnnouncementAlerts(context)
        assertEquals(update, reopened.read()?.cursor)
        manager.cancel(NOTIFICATION_ID)
        awaitGone()
        reopened.receive(baseline, update)
        reopened.receive(reopened.read()!!, update)
        assertTrue(manager.activeNotifications.none { it.id == NOTIFICATION_ID })
    }

    @Test fun optOutRejectsInflightRepliesAndReenableTakesANewBaseline() {
        alerts.enable()
        alerts.receive(alerts.read()!!, first)
        val old = alerts.read()!!
        alerts.disable()
        assertNull(scheduler.getPendingJob(PERIODIC_JOB))
        alerts.receive(old, first.copy(micros = 2_000))
        assertNull(alerts.read())
        alerts.enable()
        val fresh = alerts.read()!!
        assertNotEquals(old.activation, fresh.activation)
        alerts.receive(old, first.copy(micros = 3_000))
        assertEquals(fresh, alerts.read())
        alerts.receive(fresh, first.copy(micros = 4_000))
        assertTrue(manager.activeNotifications.none { it.id == NOTIFICATION_ID })
    }

    @Test fun channelBlockClearsConsentAndAccountChangeCancelsOldNotification() {
        alerts.enable()
        alerts.receive(alerts.read()!!, first)
        alerts.receive(alerts.read()!!, first.copy(micros = 2_000))
        awaitNotification()
        alerts.receive(alerts.read()!!, AlertCursor(UUID.randomUUID(), 5_000))
        awaitGone()
        AlertBlockReceiver().onReceive(context, Intent(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
            .putExtra(NotificationManager.EXTRA_BLOCKED_STATE, true)
            .putExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID, ALERT_CHANNEL))
        assertNull(alerts.read())
        assertNull(scheduler.getPendingJob(PERIODIC_JOB))
    }

    private fun awaitNotification(): android.service.notification.StatusBarNotification {
        repeat(100) {
            manager.activeNotifications.singleOrNull { it.id == NOTIFICATION_ID }?.let { return it }
            SystemClock.sleep(25)
        }
        throw AssertionError("Notification did not reach NotificationManager")
    }

    private fun awaitGone() {
        repeat(100) {
            if (manager.activeNotifications.none { it.id == NOTIFICATION_ID }) return
            SystemClock.sleep(25)
        }
        fail("Notification remained active")
    }
}

package com.unspokencues.wear.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.unspokencues.wear.MainActivity
import com.unspokencues.wear.R
import java.util.UUID

internal const val ALERT_CHANNEL = "event_announcements"
internal const val NOTIFICATION_ID = 419
internal data class Subscription(val activation: String, val cursor: AlertCursor?)

internal class AnnouncementAlerts(private val context: Context) {
    val prefs = context.getSharedPreferences("announcement_alerts", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(ALERT_CHANNEL, "Event announcements", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Discreet updates from events you’ve joined"
        channel.setSound(null, null)
        channel.enableVibration(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(channel)
    }

    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(ALERT_CHANNEL)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true

    fun read(): Subscription? = synchronized(lock) {
        val activation = prefs.getString("activation", null) ?: return@synchronized null
        val cursor = prefs.getString("generation", null)?.let {
            AlertCursor(UUID.fromString(it), prefs.getLong("micros", 0))
        }
        Subscription(activation, cursor)
    }

    fun enable() = synchronized(lock) {
        createChannel()
        if (allowed() && read() == null) {
            check(prefs.edit().clear().putString("activation", UUID.randomUUID().toString()).commit())
            scheduleAlertPolls(context)
        }
    }

    fun disable() = synchronized(lock) {
        check(prefs.edit().clear().commit())
        cancelAlertPolls(context)
        manager.cancel(NOTIFICATION_ID)
    }

    /** Also runs during job delivery and on return from system settings. */
    fun reconcile() = synchronized(lock) {
        if (read() != null) {
            if (!allowed()) disable() else ensurePeriodicPoll(context)
        }
    }

    @SuppressLint("MissingPermission") // checked inside the same lock just before notify
    fun receive(expected: Subscription, next: AlertCursor?) = synchronized(lock) {
        if (read() != expected) return@synchronized
        if (!allowed()) { disable(); return@synchronized }
        val previous = expected.cursor
        if (next != null && previous?.generation == next.generation && next.micros < previous.micros) return@synchronized
        if (shouldPost(previous, next)) {
            // Reserve before posting and acknowledge only after OS acceptance. If interrupted, the
            // next live poll rechecks access. An unresolved delivery can only recover silently.
            val uncertain = prefs.getBoolean("posting", false)
            check(prefs.edit().putBoolean("posting", true).commit())
            try {
                manager.notify(NOTIFICATION_ID, notification(uncertain))
            } catch (_: SecurityException) {
                disable()
                return@synchronized
            }
        } else if (next == null || previous?.generation != next.generation) {
            manager.cancel(NOTIFICATION_ID)
        }
        check(prefs.edit().putString("generation", next?.generation?.toString())
            .putLong("micros", next?.micros ?: 0).remove("posting").commit())
    }

    private fun notification(recovery: Boolean): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_announcement_notification)
            .setContentTitle("Event update")
            .setContentText("Check announcements on your phone.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (recovery) {
            // Group-alert suppression also applies if the old notification was dismissed.
            builder.setGroup("announcement_recovery").setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            builder.setOnlyAlertOnce(true)
        }
        return builder.build()
    }

    private companion object { val lock = Any() }
}

/** Android can block notifications while the activity and job are both stopped. */
class AlertBlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, false)) return
        if (intent.action == NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED ||
            (intent.action == NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED &&
                intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID) == ALERT_CHANNEL)) {
            AnnouncementAlerts(context).disable()
        }
    }
}

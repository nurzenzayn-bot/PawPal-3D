package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local notification storage and Android alarm scheduling for chat-derived reminders. */
object ConversationReminderScheduler {
    private const val PREFS = "pawpal_conversation_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ITEMS = "items"
    const val CHANNEL_ID = "pawpal_conversation_reminders"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_BODY = "reminder_body"
    const val ACTION_SNOOZE = "com.example.pawpal.action.SNOOZE_REMINDER"
    private const val SNOOZE_MINUTES = 10L
    private const val MISSED_GRACE_MILLIS = 12L * 60L * 60L * 1000L

    data class Reminder(val id: String, val title: String, val body: String, val at: Long)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            all(context).forEach {
                cancel(context, it.id)
                NotificationManagerCompat.from(context).cancel(it.id.hashCode())
            }
        } else {
            rescheduleAll(context)
        }
    }

    fun all(context: Context): List<Reminder> {
        val array = runCatching { JSONArray(prefs(context).getString(KEY_ITEMS, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val item = Reminder(o.optString("id"), o.optString("title"), o.optString("body"), o.optLong("at"))
                if (item.id.isNotBlank() && item.title.isNotBlank() && item.at > 0L) add(item)
            }
        }.sortedBy { it.at }
    }

    /** Returns the newly created reminder, or null when the text is not a clear future commitment. */
    fun captureFromMessage(context: Context, message: String): Reminder? {
        if (!isEnabled(context)) return null
        val parsed = ReminderTextParser.parse(message) ?: return null
        val id = "rem_${parsed.title.lowercase().hashCode()}_${parsed.at / 60_000L}"
        val reminder = Reminder(
            id = id,
            title = parsed.title,
            body = "Jangan lupa: ${parsed.title}. Semoga semuanya lancar! 🐾",
            at = parsed.at
        )
        val current = all(context).filterNot { it.id == id }
        save(context, current + reminder)
        schedule(context, reminder)
        return reminder
    }

    fun remove(context: Context, id: String) {
        cancel(context, id)
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        save(context, all(context).filterNot { it.id == id })
    }

    /** Recreates the reminder as a new alarm, so the snooze action still works after the first alarm fires. */
    fun snooze(context: Context, id: String?, title: String, body: String) {
        if (!isEnabled(context) || title.isBlank()) return
        if (id != null) remove(context, id)
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val reminder = Reminder(
            id = "snooze_${UUID.randomUUID()}",
            title = title,
            body = body,
            at = at
        )
        save(context, all(context) + reminder)
        schedule(context, reminder)
    }

    fun rescheduleAll(context: Context) {
        if (!isEnabled(context)) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val pending = all(context)
        val future = pending.filter { it.at > now }
        val recentlyMissed = pending.filter { it.at <= now && now - it.at <= MISSED_GRACE_MILLIS }
        val expired = pending.filter { it.at <= now && now - it.at > MISSED_GRACE_MILLIS }
        expired.forEach { cancel(context, it.id) }
        if (hasNotificationPermission(context)) {
            // If notification permission was granted after an alarm fired, deliver recent missed items once.
            recentlyMissed.forEach { reminder ->
                show(context, Intent().apply {
                    putExtra(EXTRA_ID, reminder.id)
                    putExtra(EXTRA_TITLE, reminder.title)
                    putExtra(EXTRA_BODY, reminder.body)
                })
            }
            save(context, future)
        } else {
            // Keep recently missed reminders until permission is granted or the grace period expires.
            save(context, future + recentlyMissed)
        }
        future.forEach { schedule(context, it) }
    }

    private fun schedule(context: Context, reminder: Reminder) {
        if (!isEnabled(context) || reminder.at <= System.currentTimeMillis()) return
        ensureChannel(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, reminder, PendingIntent.FLAG_UPDATE_CURRENT)
        // setAndAllowWhileIdle works without the special exact-alarm access on Android 12+.
        runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending) }
    }

    private fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ConversationReminderReceiver::class.java).setAction("pawpal.reminder.$id")
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) alarm.cancel(pending)
    }

    private fun pendingIntent(context: Context, reminder: Reminder, flags: Int): PendingIntent {
        val intent = Intent(context, ConversationReminderReceiver::class.java).apply {
            action = "pawpal.reminder.${reminder.id}"
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_BODY, reminder.body)
        }
        return PendingIntent.getBroadcast(context, reminder.id.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun show(context: Context, intent: Intent) {
        if (!isEnabled(context) || !hasNotificationPermission(context)) return
        val id = intent.getStringExtra(EXTRA_ID)
        // Remove only after permission is confirmed; otherwise the reminder remains recoverable.
        if (id != null) save(context, all(context).filterNot { it.id == id })
        ensureChannel(context)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Pengingat PawPal"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Ada agenda yang pernah kamu ceritakan."
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            (id ?: title).hashCode(),
            Intent(context, ConversationReminderActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🐾 Jangan lupa: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Tunda 10 menit", snooze)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id?.hashCode() ?: 9100, notification) }
    }

    internal fun handleSnoozeAction(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Pengingat dari PawPal."
        snooze(context, id, title, body)
        if (id != null) NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pengingat dari percakapan", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Agenda yang kamu ceritakan kepada PawPal, diingatkan pada waktunya."
                }
            )
        }
    }

    private fun save(context: Context, reminders: List<Reminder>) {
        val unique = reminders.distinctBy { it.id }.sortedBy { it.at }
        val array = JSONArray()
        unique.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("at", it.at))
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ConversationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ConversationReminderScheduler.show(context, intent)
}

class ConversationReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConversationReminderScheduler.ACTION_SNOOZE) {
            ConversationReminderScheduler.handleSnoozeAction(context, intent)
        }
    }
}

class ConversationReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ConversationReminderScheduler.rescheduleAll(context)
        }
    }
}

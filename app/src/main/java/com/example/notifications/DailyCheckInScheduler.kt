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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.MoodJournalRepository
import com.example.model.CharacterCatalog
import com.example.model.PetMemoryManager
import java.util.Calendar

object DailyCheckInScheduler {
    private const val TAG = "DailyCheckInScheduler"
    const val CHANNEL_ID = "pawpal_daily_checkin"
    private const val PREFS_NAME = "pawpal_reminder_prefs"
    private const val KEY_ENABLED = "daily_reminders_enabled"
    private const val KEY_MORNING_HOUR = "morning_hour"
    private const val KEY_EVENING_HOUR = "evening_hour"

    const val EXTRA_SLOT = "extra_checkin_slot"
    const val SLOT_MORNING = "morning"
    const val SLOT_EVENING = "evening"

    private const val REQ_MORNING = 401
    private const val REQ_EVENING = 402
    private const val NOTIF_ID_MORNING = 9001
    private const val NOTIF_ID_EVENING = 9002
    private const val ACTION_PREFIX = "com.example.action.DAILY_CHECKIN_"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun getMorningHour(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MORNING_HOUR, 7)
    }

    fun getEveningHour(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_EVENING_HOUR, 20)
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) {
            ensureNotificationChannel(context)
            scheduleMorningAndEvening(context)
        } else {
            cancelAll(context)
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sapaan Harian & Cek Mood"
            val desc = "Pengingat pagi dan malam yang ramah dari sahabat 3D kamu."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun scheduleMorningAndEvening(context: Context) {
        if (!isEnabled(context)) return
        ensureNotificationChannel(context)
        scheduleSlot(context, SLOT_MORNING, getMorningHour(context), 30, REQ_MORNING)
        scheduleSlot(context, SLOT_EVENING, getEveningHour(context), 15, REQ_EVENING)
    }

    private fun scheduleSlot(
        context: Context,
        slot: String,
        hourOfDay: Int,
        minute: Int,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyCheckInReceiver::class.java).apply {
            action = ACTION_PREFIX + slot
            putExtra(EXTRA_SLOT, slot)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d(TAG, "Scheduled $slot daily check-in at $hourOfDay:$minute")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule $slot alarm: ${e.message}")
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        listOf(REQ_MORNING to SLOT_MORNING, REQ_EVENING to SLOT_EVENING).forEach { (req, slot) ->
            val intent = Intent(context, DailyCheckInReceiver::class.java).apply {
                action = ACTION_PREFIX + slot
                putExtra(EXTRA_SLOT, slot)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
            }
        }
    }

    fun showMoodAwareNotificationNow(context: Context, slot: String) {
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification")
                return
            }
        }

        val memoryManager = PetMemoryManager(context)
        val nickname = memoryManager.getUserNickname().ifBlank { "Sobat" }
        val activeCharId = context.getSharedPreferences("pawpal_ai_prefs", Context.MODE_PRIVATE)
            .getString("selected_character_id", "zayn") ?: "zayn"
        val character = CharacterCatalog.getById(activeCharId)
        val latestMood = MoodJournalRepository(context).getEntries().firstOrNull()

        val (title, body) = buildMoodAwareMessage(
            slot = slot,
            nickname = nickname,
            characterName = character.name,
            characterEmoji = character.species.emoji,
            latestMoodLabel = latestMood?.mood?.label,
            latestMoodNote = latestMood?.userNote
        )

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notifId = if (slot == SLOT_EVENING) NOTIF_ID_EVENING else NOTIF_ID_MORNING
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${se.message}")
        }
    }

    private fun buildMoodAwareMessage(
        slot: String,
        nickname: String,
        characterName: String,
        characterEmoji: String,
        latestMoodLabel: String?,
        latestMoodNote: String?
    ): Pair<String, String> {
        val isMorning = slot == SLOT_MORNING
        val moodSuffix = if (!latestMoodLabel.isNullOrBlank()) {
            " Terakhir kamu merasa $latestMoodLabel."
        } else ""

        return if (isMorning) {
            "$characterEmoji Pagi $nickname! — $characterName" to
                "Yuk mulai pagi dengan semangat!$moodSuffix Mau latihan bahasa atau ngobrol bentar sebelum aktivitas?"
        } else {
            "$characterEmoji Malam $nickname! — $characterName" to
                if (!latestMoodNote.isNullOrBlank()) {
                    "Gimana harimu hari ini? Ingat catatanmu: \"${latestMoodNote.take(45)}\". Yuk cerita ke aku sebelum istirahat!"
                } else {
                    "Hari sudah malam!$moodSuffix Yuk catat perasaanmu di Jurnal Mood & klaim koin harian."
                }
        }
    }
}

class DailyCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(DailyCheckInScheduler.EXTRA_SLOT)
            ?: DailyCheckInScheduler.SLOT_MORNING
        DailyCheckInScheduler.showMoodAwareNotificationNow(context, slot)
    }
}

class DailyCheckInBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            DailyCheckInScheduler.scheduleMorningAndEvening(context)
        }
    }
}

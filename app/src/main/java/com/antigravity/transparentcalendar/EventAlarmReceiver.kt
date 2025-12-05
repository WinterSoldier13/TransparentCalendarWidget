package com.antigravity.transparentcalendar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "event_reminders"
        const val CHANNEL_NAME = "Event Reminders"
        const val NOTIFICATION_ID = 123
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("EventAlarmReceiver", "Alarm received")

        val prefs = context.getSharedPreferences("com.antigravity.transparentcalendar.prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("PREF_FULL_SCREEN_REMINDER", false)

        if (!isEnabled) {
            Log.d("EventAlarmReceiver", "Full screen reminder disabled")
            CalendarUpdateJobService.scheduleJob(context)
            return
        }

        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "Event"
        val eventId = intent.getLongExtra("EVENT_ID", -1)
        val eventStart = intent.getLongExtra("EVENT_START", 0)

        // Mark as handled immediately so scheduler sees it
        if (eventId != -1L) {
            NotificationScheduler.markEventAsHandled(context, eventId, eventStart)
        }

        createNotificationChannel(context)

        val fullScreenIntent = Intent(context, EventNotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EVENT_TITLE", eventTitle)
            putExtra("EVENT_ID", eventId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(), // Use event ID for unique request code if needed, or 0
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher) // Fallback icon, ensure it exists. ic_launcher usually does.
            .setContentTitle("Event Starting")
            .setContentText(eventTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        // Schedule next alarm immediately
        NotificationScheduler.refreshSchedule(context)

        // Also ensure job is scheduled for future content changes
        CalendarUpdateJobService.scheduleJob(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full screen notifications for calendar events"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

package com.antigravity.transparentcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val PREFS_NAME = "com.antigravity.transparentcalendar.scheduler"
    private const val KEY_LAST_HANDLED_TIME = "last_handled_time"
    private const val KEY_HANDLED_IDS = "handled_ids"

    fun refreshSchedule(context: Context, onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                val events = fetchUpcomingEvents(context)
                scheduleNextEvent(context, events)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing schedule", e)
            } finally {
                onComplete?.invoke()
            }
        }.start()
    }

    fun markEventAsHandled(context: Context, eventId: Long, eventStart: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_HANDLED_TIME, 0)
        val handledIds = prefs.getStringSet(KEY_HANDLED_IDS, emptySet()) ?: emptySet()

        val newHandledIds = HashSet<String>()

        if (eventStart > lastTime) {
            // New time block, clear old IDs and start fresh
            newHandledIds.add(eventId.toString())
            prefs.edit()
                .putLong(KEY_LAST_HANDLED_TIME, eventStart)
                .putStringSet(KEY_HANDLED_IDS, newHandledIds)
                .apply()
        } else if (eventStart == lastTime) {
            // Same time block, append ID
            newHandledIds.addAll(handledIds)
            newHandledIds.add(eventId.toString())
            prefs.edit()
                .putStringSet(KEY_HANDLED_IDS, newHandledIds)
                .apply()
        }
        // If eventStart < lastTime, it's an old event re-firing? Ignore or log.
        // But we shouldn't update the "future" pointer backwards.
    }

    private fun fetchUpcomingEvents(context: Context): List<EventModel> {
        val events = ArrayList<EventModel>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return events
        }

        val projection = arrayOf(
            android.provider.CalendarContract.Instances.EVENT_ID,
            android.provider.CalendarContract.Instances.TITLE,
            android.provider.CalendarContract.Instances.BEGIN,
            android.provider.CalendarContract.Instances.END,
            android.provider.CalendarContract.Instances.DISPLAY_COLOR,
            android.provider.CalendarContract.Instances.ALL_DAY
        )

        val now = System.currentTimeMillis()
        val endRange = now + (android.text.format.DateUtils.DAY_IN_MILLIS * 30) // Next 30 days

        val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.BEGIN} <= ?"
        val selectionArgs = arrayOf(now.toString(), endRange.toString())

        val builder = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, now)
        android.content.ContentUris.appendId(builder, endRange)

        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                val beginIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.END)
                val colorIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.DISPLAY_COLOR)
                val allDayIndex = it.getColumnIndex(android.provider.CalendarContract.Instances.ALL_DAY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "No Title"
                    val start = it.getLong(beginIndex)
                    val end = it.getLong(endIndex)
                    val color = if (colorIndex != -1) it.getInt(colorIndex) else 0
                    val isAllDay = if (allDayIndex != -1) it.getInt(allDayIndex) == 1 else false

                    events.add(EventModel(id, title, start, end, color, isAllDay))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }

    fun scheduleNextEvent(context: Context, events: List<EventModel>) {
        val prefs = context.getSharedPreferences("com.antigravity.transparentcalendar.prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("PREF_FULL_SCREEN_REMINDER", false)) {
            Log.d(TAG, "Full screen reminder disabled, cancelling any existing alarms")
            cancelAlarm(context)
            return
        }

        val schedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastHandledTime = schedPrefs.getLong(KEY_LAST_HANDLED_TIME, 0)
        val handledIds = schedPrefs.getStringSet(KEY_HANDLED_IDS, emptySet()) ?: emptySet()

        val now = System.currentTimeMillis()
        val tolerance = 60000L // 1 minute
        val searchStart = now - tolerance

        // Filter candidates
        val candidates = events.filter { it.start >= searchStart }

        val nextEvent = candidates.filter { event ->
            if (event.start > lastHandledTime) {
                true // New future event
            } else if (event.start == lastHandledTime) {
                !handledIds.contains(event.id.toString()) // Not yet handled in this time block
            } else {
                false // Already handled in a previous time block
            }
        }.minByOrNull { it.start }

        if (nextEvent == null) {
            Log.d(TAG, "No upcoming events found to schedule")
            cancelAlarm(context)
            return
        }

        Log.d(TAG, "Scheduling alarm for event: ${nextEvent.title} at ${nextEvent.start}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            putExtra("EVENT_TITLE", nextEvent.title)
            putExtra("EVENT_ID", nextEvent.id)
            putExtra("EVENT_START", nextEvent.start)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                 alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextEvent.start, pendingIntent)
            } else {
                 alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextEvent.start, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error scheduling alarm", e)
            // Ideally we should notify the user here if this runs in foreground or if we can show a notification
            // but since this might run in bg, we just log.
            // However, let's try to post a notification if we have permission, so the user knows why it failed.
            try {
                 val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                 val errorChannelId = "error_channel"
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                     val channel = android.app.NotificationChannel(errorChannelId, "Errors", android.app.NotificationManager.IMPORTANCE_HIGH)
                     notificationManager.createNotificationChannel(channel)
                 }

                 val builder = androidx.core.app.NotificationCompat.Builder(context, errorChannelId)
                     .setSmallIcon(android.R.drawable.ic_dialog_alert)
                     .setContentTitle("Calendar Widget Error")
                     .setContentText("Cannot schedule exact alarms. Please enable 'Alarms & Reminders' in Settings.")
                     .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                     .setAutoCancel(true)
                     
                 notificationManager.notify(999, builder.build())
            } catch (ex: Exception) {
               Log.e(TAG, "Could not show error notification", ex)
            }
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled existing alarm")
        }
    }
}

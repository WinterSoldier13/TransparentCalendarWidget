package com.antigravity.transparentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, scheduling jobs")
            // Schedule next alarm immediately
            NotificationScheduler.refreshSchedule(context)
            // Also register content observer via JobScheduler
            CalendarUpdateJobService.scheduleJob(context)
        }
    }
}

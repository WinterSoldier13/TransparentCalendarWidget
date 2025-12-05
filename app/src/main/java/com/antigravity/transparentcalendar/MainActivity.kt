package com.antigravity.transparentcalendar


import android.Manifest
import android.app.NotificationManager // Added import
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted! You can now add the widget.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Permission denied. The widget will not show events.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissionAndRequest()

        val switch = findViewById<android.widget.Switch>(R.id.enable_notifications_switch)
        val prefs = getSharedPreferences("com.antigravity.transparentcalendar.prefs", MODE_PRIVATE)
        switch.isChecked = prefs.getBoolean("PREF_FULL_SCREEN_REMINDER", false)

        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("PREF_FULL_SCREEN_REMINDER", isChecked).apply()
            // Trigger update to schedule/cancel alarms
            NotificationScheduler.refreshSchedule(this)
            CalendarUpdateJobService.scheduleJob(this)

            if (isChecked) {
                 checkNotificationPermission()
                 checkFullScreenIntentPermission() // ADDED: Check when toggled on
                 checkExactAlarmPermission() // ADDED: Check exact alarm when toggled
            }
        }

        findViewById<android.widget.Button>(R.id.test_notification_button).setOnClickListener {
            android.widget.Toast.makeText(this, "Lock screen NOW! Notification in 5s...", android.widget.Toast.LENGTH_LONG).show()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                triggerTestNotification()
            }, 5000)
        }


        // Also check notification permission if enabled on start
        if (switch.isChecked) {
            checkNotificationPermission()
            checkFullScreenIntentPermission() // ADDED: Check for full screen intent permission
            checkExactAlarmPermission() // ADDED: Check exact alarm permission
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("To show full-screen reminders on the lock screen, please allow 'Full Screen Intent' permission in the next screen.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                         startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Exact Alarm Permission Required")
                    .setMessage("To trigger reminders at the exact event time, please allow 'Alarms & Reminders' permission in the next screen.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun triggerTestNotification() {
        val intent = android.content.Intent(this, EventAlarmReceiver::class.java).apply {
            putExtra("EVENT_TITLE", "Test Event")
            putExtra("EVENT_ID", 999L)
            putExtra("EVENT_START", System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }


    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkPermissionAndRequest() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }
}

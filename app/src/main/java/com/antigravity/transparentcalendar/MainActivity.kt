package com.antigravity.transparentcalendar

import android.Manifest
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
            }
        }

        // Also check notification permission if enabled on start
        if (switch.isChecked) {
            checkNotificationPermission()
        }
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
